package ai.javaclaw.channels.whatsapp.onboarding;

import it.auties.whatsapp.api.QrHandler;
import it.auties.whatsapp.api.WebHistorySetting;
import it.auties.whatsapp.api.Whatsapp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


@Service
public class WhatsappOnboardingLinkService {
    private static final Logger log = LoggerFactory.getLogger(WhatsappOnboardingLinkService.class);

    public record Snapshot(
            String sessionAlias,
            boolean connected,
            boolean linked,
            String qrDataUriPng,
            String qrRaw,
            String error,
            Instant updatedAt,
            String sessionPath
    ) {
    }

    private static final class State {
        final String sessionAlias;
        final AtomicBoolean started = new AtomicBoolean(false);
        volatile Whatsapp client;
        volatile CompletableFuture<Whatsapp> connectFuture;
        volatile boolean linked;
        volatile boolean connected;
        volatile String qrRaw;
        volatile String qrDataUriPng;
        volatile String error;
        volatile Instant updatedAt = Instant.now();

        State(String sessionAlias) {
            this.sessionAlias = sessionAlias;
        }
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();

    public void startOrAttach(String key, String sessionAlias) {
        var state = states.compute(key, (k, existing) -> {
            if (existing == null) {
                return new State(sessionAlias);
            }

            if (!existing.sessionAlias.equals(sessionAlias)) {
                safeDisconnect(existing);
                return new State(sessionAlias);
            }
            return existing;
        });

        if (!state.started.compareAndSet(false, true)) {
            return;
        }

        try {
            var options = Whatsapp.webBuilder()
                    .newConnection(sessionAlias)

                    .historySetting(WebHistorySetting.discard(false))
                    .automaticMessageReceipts(true);


            var whatsapp = options.registered().orElseGet(() ->
                    options.unregistered(QrHandler.toPlainString(qr -> onQr(state, qr))));

            state.client = whatsapp;

            whatsapp.addLoggedInListener(api -> {
                state.linked = true;
                state.connected = api.isConnected();
                state.updatedAt = Instant.now();
                log.info("WhatsApp linked during onboarding (sessionAlias={})", sessionAlias);
            });

            whatsapp.addDisconnectedListener(reason -> {
                state.connected = false;
                state.updatedAt = Instant.now();
                log.warn("WhatsApp onboarding client disconnected: {}", reason);
            });

            state.connectFuture = whatsapp.connect().whenComplete((api, err) -> {
                if (err != null) {
                    state.error = String.valueOf(err.getMessage());
                    state.updatedAt = Instant.now();
                    log.warn("WhatsApp onboarding connect failed", err);
                    return;
                }
                state.connected = api.isConnected();
                if (state.connected) {
                    state.linked = true;
                }
                state.updatedAt = Instant.now();
            });
        } catch (Throwable t) {
            state.error = String.valueOf(t.getMessage());
            state.updatedAt = Instant.now();
            log.warn("Failed to start WhatsApp onboarding flow", t);
        }
    }

    public boolean isLinked(String key) {
        var state = states.get(key);
        if (state == null) {
            return false;
        }

        var client = state.client;
        return state.linked || state.connected || (client != null && client.isConnected());
    }

    public Optional<Snapshot> snapshot(String key) {
        var state = states.get(key);
        if (state == null) {
            return Optional.empty();
        }

        var home = System.getProperty("user.home");
        Path sessionPath = Path.of(home, ".whatsapp4j", "web", state.sessionAlias);

        return Optional.of(new Snapshot(
                state.sessionAlias,
                state.connected,
                state.linked,
                state.qrDataUriPng,
                state.qrRaw,
                state.error,
                state.updatedAt,
                sessionPath.toString()
        ));
    }

    private void onQr(State state, String qr) {
        state.qrRaw = qr;
        state.updatedAt = Instant.now();

        try {
            var matrix = QrHandler.createMatrix(qr, 512, 512);
            var cropped = cropToCode(matrix);
            var padded = addPadding(cropped, 16);
            var img = toImage(padded);
            var bytes = toPng(img);
            state.qrDataUriPng = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Throwable t) {
            state.qrDataUriPng = null;
        }
    }

    private static com.google.zxing.common.BitMatrix cropToCode(com.google.zxing.common.BitMatrix matrix) {
        int[] rect = matrix.getEnclosingRectangle();
        if (rect == null) {
            return matrix;
        }

        int left = rect[0];
        int top = rect[1];
        int width = rect[2];
        int height = rect[3];

        if (width <= 0 || height <= 0) {
            return matrix;
        }

        var cropped = new com.google.zxing.common.BitMatrix(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(left + x, top + y)) {
                    cropped.set(x, y);
                }
            }
        }
        return cropped;
    }

    private static com.google.zxing.common.BitMatrix addPadding(com.google.zxing.common.BitMatrix matrix, int padPx) {
        if (padPx <= 0) {
            return matrix;
        }

        int width = matrix.getWidth();
        int height = matrix.getHeight();
        var padded = new com.google.zxing.common.BitMatrix(width + padPx * 2, height + padPx * 2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    padded.set(x + padPx, y + padPx);
                }
            }
        }
        return padded;
    }

    private static BufferedImage toImage(com.google.zxing.common.BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int on = 0x000000;
        int off = 0xFFFFFF;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? on : off);
            }
        }
        return image;
    }

    private static byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    private static void safeDisconnect(State state) {
        try {
            var c = state.client;
            if (c != null) {
                c.disconnect();
            }
        } catch (Throwable ignored) {
        }
    }
}

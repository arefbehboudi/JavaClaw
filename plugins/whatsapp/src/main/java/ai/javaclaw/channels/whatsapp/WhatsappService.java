package ai.javaclaw.channels.whatsapp;

import it.auties.whatsapp.api.Listener;
import it.auties.whatsapp.api.QrHandler;
import it.auties.whatsapp.api.WebHistorySetting;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.model.info.ChatMessageInfo;
import it.auties.whatsapp.model.info.NewsletterMessageInfo;
import it.auties.whatsapp.model.jid.Jid;
import it.auties.whatsapp.model.jid.JidServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;


public class WhatsappService {
    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);

    private final WhatsappProperties properties;
    private final AtomicReference<Whatsapp> clientRef = new AtomicReference<>();

    public WhatsappService(WhatsappProperties properties) {
        this.properties = properties;
    }

    public void start(BiConsumer<Whatsapp, ChatMessageInfo> onIncomingChatMessage) {

        var alias = properties.effectiveSessionAlias();
        log.info("Starting WhatsApp integration (sessionAlias={})", alias);

        var options = Whatsapp
                .webBuilder()
                .newConnection(alias);

        var whatsapp = options.registered()
                .orElseGet(() -> options.unregistered(QrHandler.toPlainString(qr ->
                        log.info("WhatsApp requires linking. Use the onboarding step to scan the QR (sessionAlias={}).", alias)
                )));

        whatsapp.addLoggedInListener(_ -> log.info("WhatsApp logged in (sessionAlias={})", alias));
        whatsapp.addDisconnectedListener(reason -> log.warn("WhatsApp disconnected: {}", reason));
        whatsapp.addNewChatMessageListener(onIncomingChatMessage::accept);


        clientRef.set(whatsapp);
        whatsapp.connect()
                .whenComplete((api, err) -> {
                    if (err != null) {
                        log.error("WhatsApp connection failed", err);
                    }
                });
    }

    public Path defaultSessionPath() {
        // Cobalt default session path:
        // macOS/Linux: $HOME/.whatsapp4j/web/<alias>/
        // Windows:     %USERPROFILE%\\.whatsapp4j\\web\\<alias>\\
        return Path.of(System.getProperty("user.home"), ".whatsapp4j", "web", properties.effectiveSessionAlias());
    }

    public void sendTextMessage(String to, String message) {
        var api = clientRef.get();
        if (api == null) {
            throw new IllegalStateException("WhatsApp is not initialized yet (client is null).");
        }

        var destination = parseDestination(to);
        api.sendMessage(destination, message);
    }

    private static Jid parseDestination(String to) {
        var trimmed = to == null ? "" : to.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Missing destination 'to'");
        }

        if (trimmed.contains("@")) {
            return Jid.of(trimmed);
        }

        var normalized = trimmed.replace("+", "").replaceAll("\\s+", "");
        return Jid.of(normalized, JidServer.whatsapp());
    }
}

package ai.javaclaw.channels.whatsapp.onboarding;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNullElse;

@RestController
public class WhatsappOnboardingQrController {
    private final WhatsappOnboardingLinkService linkService;

    public WhatsappOnboardingQrController(WhatsappOnboardingLinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping(value = "/onboarding/whatsapp/fragment", produces = MediaType.TEXT_HTML_VALUE)
    public String fragment(@RequestParam(name = "key", required = false) String key) {
        if (key == null || key.isBlank()) {
            return """
                    <article class="message is-warning">
                      <div class="message-body">Missing onboarding key.</div>
                    </article>
                    """;
        }

        var snapshotOpt = linkService.snapshot(key);
        if (snapshotOpt.isEmpty()) {
            return """
                    <article class="message is-warning">
                      <div class="message-body">No WhatsApp onboarding session found. Click Generate QR again.</div>
                    </article>
                    """;
        }

        var s = snapshotOpt.get();
        if (s.linked() || s.connected()) {
            return """
                    <article class="message is-success" id="whatsapp-link-fragment">
                      <div class="message-body">
                        <p><strong>Linked.</strong> WhatsApp is connected to this server.</p>
                        <p class="mt-2"><small>Session stored at: %s</small></p>
                        <p class="mt-2"><small>You can click Continue to proceed.</small></p>
                      </div>
                    </article>
                    <div id="whatsapp-link-fragment" hx-swap-oob="outerHTML">
                      <article class="message is-success">
                        <div class="message-body">
                          <p><strong>Linked.</strong> WhatsApp is connected to this server.</p>
                          <p class="mt-2"><small>Session stored at: %s</small></p>
                        </div>
                      </article>
                    </div>
                    <button id="whatsapp-continue-btn" class="button is-primary is-medium" type="submit" hx-swap-oob="outerHTML">Continue</button>
                    """.formatted(escapeHtml(s.sessionPath()), escapeHtml(s.sessionPath()));
        }

        var error = requireNonNullElse(s.error(), "").trim();
        if (!error.isBlank()) {
            return """
                    <article class="message is-danger">
                      <div class="message-body">
                        <p><strong>Connection error.</strong> %s</p>
                        <p class="mt-2"><small>Fix the issue, then click Generate QR again.</small></p>
                      </div>
                    </article>
                    <button id="whatsapp-continue-btn" class="button is-primary is-medium" type="submit" hx-swap-oob="outerHTML" disabled aria-disabled="true">Waiting for scan...</button>
                    """.formatted(escapeHtml(error));
        }

        var img = requireNonNullElse(s.qrDataUriPng(), "").trim();
        var raw = requireNonNullElse(s.qrRaw(), "").trim();
        if (!img.isBlank()) {
            return """
                    <div class="box">
                      <p class="has-text-grey mb-2"><small>Status: QR ready. Waiting for scan...</small></p>
                      <p class="has-text-weight-semibold mb-2">Scan this QR code in WhatsApp</p>
                      <figure class="image" style="max-width: 520px; margin: 0 auto;">
                        <img src="%s" alt="WhatsApp QR code" style="width: 100%%; height: auto; image-rendering: pixelated;">
                      </figure>
                      <p class="mt-3"><small>WhatsApp → Settings → Linked devices → Link a device</small></p>
                      <p class="mt-2"><small>Session will be stored at: %s</small></p>
                    </div>
                    <button id="whatsapp-continue-btn" class="button is-primary is-medium" type="submit" hx-swap-oob="outerHTML" disabled aria-disabled="true">Waiting for scan...</button>
                    """.formatted(img, escapeHtml(s.sessionPath()));
        }

        if (!raw.isBlank()) {
            return """
                    <div class="box">
                      <p class="has-text-grey mb-2"><small>Status: QR ready. Waiting for scan...</small></p>
                      <p class="has-text-weight-semibold mb-2">QR payload (could not render image)</p>
                      <pre style="white-space: pre-wrap;">%s</pre>
                      <p class="mt-2"><small>Session will be stored at: %s</small></p>
                    </div>
                    <button id="whatsapp-continue-btn" class="button is-primary is-medium" type="submit" hx-swap-oob="outerHTML" disabled aria-disabled="true">Waiting for scan...</button>
                    """.formatted(escapeHtml(raw), escapeHtml(s.sessionPath()));
        }

        return """
                <article class="message is-info">
                  <div class="message-body">
                    <p><strong>Connecting…</strong></p>
                    <p class="mt-2"><small>Status: loading/restoring session or waiting for QR generation.</small></p>
                    <p class="mt-2"><small>This page updates automatically.</small></p>
                  </div>
                </article>
                <button id="whatsapp-continue-btn" class="button is-primary is-medium" type="submit" hx-swap-oob="outerHTML" disabled aria-disabled="true">Waiting for scan...</button>
                """;
    }

    private static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

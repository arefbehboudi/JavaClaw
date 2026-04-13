package ai.javaclaw.channels.whatsapp.onboarding;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class WhatsappOnboardingActionsController {
    private final WhatsappOnboardingLinkService linkService;

    public WhatsappOnboardingActionsController(WhatsappOnboardingLinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping(value = "/onboarding/whatsapp/start", produces = MediaType.TEXT_HTML_VALUE)
    public String start(@RequestParam(name = "whatsappSessionAlias", required = false) String alias,
                        @RequestParam(name = "whatsappAllowedChatJid", required = false) String allowedChatJid,
                        HttpSession session) {
        var trimmedAlias = alias == null ? "" : alias.trim();
        if (trimmedAlias.isBlank()) {
            return """
                    <article class="message is-danger" id="whatsapp-link-fragment-container">
                      <div class="message-body">Enter a session alias first.</div>
                    </article>
                    """;
        }

        session.setAttribute(WhatsappOnboardingSessionKeys.SESSION_ALIAS, trimmedAlias);
        var trimmedAllowed = allowedChatJid == null ? "" : allowedChatJid.trim();
        if (!trimmedAllowed.isBlank()) {
            session.setAttribute(WhatsappOnboardingSessionKeys.ALLOWED_CHAT_JID, trimmedAllowed);
        }

        String key = (String) session.getAttribute(WhatsappOnboardingSessionKeys.CONN_KEY);
        if (key == null || key.isBlank()) {
            key = UUID.randomUUID().toString();
            session.setAttribute(WhatsappOnboardingSessionKeys.CONN_KEY, key);
        }

        linkService.startOrAttach(key, trimmedAlias);

        return """
                <div id="whatsapp-link-fragment-container">
                  <div id="whatsapp-link-fragment"
                       hx-get="/onboarding/whatsapp/fragment?key=%s"
                       hx-trigger="load delay:500ms, every 2s"
                       hx-swap="innerHTML">
                    <article class="message is-info">
                      <div class="message-body">Connecting…</div>
                    </article>
                  </div>
                </div>
                <button id="whatsapp-continue-btn" class="button is-primary is-medium" type="submit" hx-swap-oob="outerHTML" disabled aria-disabled="true">Waiting for scan...</button>
                """.formatted(escapeHtmlAttr(key));
    }

    private static String escapeHtmlAttr(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&#39;");
    }
}

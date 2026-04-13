package ai.javaclaw.channels.whatsapp;

import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.channels.whatsapp.onboarding.WhatsappOnboardingLinkService;
import ai.javaclaw.channels.whatsapp.onboarding.WhatsappOnboardingSessionKeys;
import ai.javaclaw.onboarding.OnboardingProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@Order(55)
public class WhatsappOnboardingProvider implements OnboardingProvider {

    private static final String ENABLED_PROPERTY = "agent.channels.whatsapp.enabled";
    private static final String ALIAS_PROPERTY = "agent.channels.whatsapp.session-alias";
    private static final String ALLOWED_CHAT_JID_PROPERTY = "agent.channels.whatsapp.allowed-chat-jid";

    private final Environment env;
    private final WhatsappOnboardingLinkService linkService;

    public WhatsappOnboardingProvider(Environment env, WhatsappOnboardingLinkService linkService) {
        this.env = env;
        this.linkService = linkService;
    }

    @Override
    public boolean isOptional() {
        return true;
    }

    @Override
    public String getStepId() {
        return "whatsapp";
    }

    @Override
    public String getStepTitle() {
        return "WhatsApp";
    }

    @Override
    public String getTemplatePath() {
        return "onboarding/steps/whatsapp";
    }

    @Override
    public void prepareModel(Map<String, Object> session, Map<String, Object> model) {
        model.put("whatsappSessionAlias", session.getOrDefault(
                WhatsappOnboardingSessionKeys.SESSION_ALIAS, env.getProperty(ALIAS_PROPERTY, "javaclaw-whatsapp")));

        String connKey = (String) session.getOrDefault(WhatsappOnboardingSessionKeys.CONN_KEY, "");
        model.put("whatsappConnKey", connKey);
        model.put("whatsappLinked", !connKey.isBlank() && linkService.isLinked(connKey));

        model.put("whatsappAllowedChatJid", session.getOrDefault(
                WhatsappOnboardingSessionKeys.ALLOWED_CHAT_JID, env.getProperty(ALLOWED_CHAT_JID_PROPERTY, "")));
    }

    @Override
    public String processStep(Map<String, String> formParams, Map<String, Object> session) {
        String alias = formParams.getOrDefault("whatsappSessionAlias", "").trim();
        String allowedChatJid = formParams.getOrDefault("whatsappAllowedChatJid", "").trim();

        if (alias.isBlank()) {
            return "Enter a session alias to continue (used for session persistence).";
        }

        session.put(WhatsappOnboardingSessionKeys.SESSION_ALIAS, alias);
        if (!allowedChatJid.isBlank()) {
            session.put(WhatsappOnboardingSessionKeys.ALLOWED_CHAT_JID, allowedChatJid);
        }

        String connKey = (String) session.get(WhatsappOnboardingSessionKeys.CONN_KEY);
        if (connKey == null || connKey.isBlank()) {
            return "Click 'Generate QR' to start linking WhatsApp, then scan the QR code.";
        }
        if (!linkService.isLinked(connKey)) {
            return "Waiting for WhatsApp linking. Scan the QR code, then click Continue.";
        }

        if (allowedChatJid.isBlank()) {
            return "Enter the allowed WhatsApp chat JID/phone (only this chat can control the agent).";
        }

        session.put(WhatsappOnboardingSessionKeys.ENABLED, "true");
        return null;
    }

    @Override
    public void saveConfiguration(Map<String, Object> session, ConfigurationManager configurationManager) throws IOException {
        var enabled = (String) session.get(WhatsappOnboardingSessionKeys.ENABLED);
        var alias = (String) session.get(WhatsappOnboardingSessionKeys.SESSION_ALIAS);
        var allowedChatJid = (String) session.get(WhatsappOnboardingSessionKeys.ALLOWED_CHAT_JID);

        if ("true".equalsIgnoreCase(enabled) && alias != null && !alias.isBlank() && allowedChatJid != null && !allowedChatJid.isBlank()) {
            configurationManager.updateProperties(Map.of(
                    ENABLED_PROPERTY, "true",
                    ALIAS_PROPERTY, alias,
                    ALLOWED_CHAT_JID_PROPERTY, allowedChatJid
            ));
        }
    }
}

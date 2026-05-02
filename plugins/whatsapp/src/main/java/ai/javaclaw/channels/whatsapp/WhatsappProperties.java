package ai.javaclaw.channels.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.channels.whatsapp")
public record WhatsappProperties(
        boolean enabled,
        String sessionAlias,
        String allowedChatJid
) {
    public String effectiveSessionAlias() {
        var alias = sessionAlias == null ? "" : sessionAlias.trim();
        return alias.isBlank() ? "javaclaw-whatsapp" : alias;
    }

    public String normalizedAllowedChatJid() {
        var raw = allowedChatJid == null ? "" : allowedChatJid.trim();
        return raw.isBlank() ? null : raw;
    }
}

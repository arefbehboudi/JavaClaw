package ai.javaclaw.channels.whatsapp;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelMessageReceivedEvent;
import ai.javaclaw.channels.ChannelRegistry;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.model.info.ChatMessageInfo;
import it.auties.whatsapp.model.jid.Jid;
import it.auties.whatsapp.model.jid.JidServer;
import it.auties.whatsapp.model.message.standard.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public class WhatsappChannel implements Channel {
    private static final Logger log = LoggerFactory.getLogger(WhatsappChannel.class);

    private final WhatsappService whatsappService;
    private final Agent agent;
    private final ChannelRegistry channelRegistry;

    private final Jid allowedChatJid;
    private final AtomicReference<Jid> lastChatJid = new AtomicReference<>();

    public WhatsappChannel(WhatsappService whatsappService,
                           WhatsappProperties properties,
                           Agent agent,
                           ChannelRegistry channelRegistry) {
        this.whatsappService = whatsappService;
        this.agent = agent;
        this.channelRegistry = channelRegistry;
        this.allowedChatJid = normalizeAllowedChatJid(properties.normalizedAllowedChatJid());

        channelRegistry.registerChannel(this);
        whatsappService.start(this::onIncomingChatMessage);
        log.info("Started WhatsApp integration (allowedChatJid={})", allowedChatJid);
    }

    private void onIncomingChatMessage(Whatsapp api, ChatMessageInfo info) {
        if (info.fromMe()) {
            log.info("Received chat message from me {}", tryExtractText(info).get());
            return;
        }

        var chatJid = info.chatJid();

        if (!isAllowedChat(info)) {
            return;
        }

        try {
            api.markMessageRead(info)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .get();
        } catch (Throwable t) {
            log.debug("Failed to start markMessageRead", t);
        }

        lastChatJid.set(chatJid);

        var text = tryExtractText(info).orElse(null);
        if (text == null) {
            return;
        }

        channelRegistry.publishMessageReceivedEvent(new ChannelMessageReceivedEvent(getName(), text));

        String response = agent.respondTo(getConversationId(chatJid), text);
        api.sendMessage(chatJid, response);
    }

    @Override
    public void sendMessage(String message) {
        var chat = lastChatJid.get();
        if (chat == null) {
            log.error("No known WhatsApp chat, cannot send message '{}'", message);
            return;
        }

        try {
            whatsappService.sendTextMessage(chat.toString(), message);
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp message", e);
        }
    }

    private boolean isAllowedChat(ChatMessageInfo info) {
        if (allowedChatJid == null) {
            return false;
        }
        var chat = info.chatJid();
        return chat != null && chat.toSimpleJid().equals(allowedChatJid.toSimpleJid());
    }

    private static String getConversationId(Jid chatJid) {
        return "whatsapp-" + chatJid;
    }

    private static Jid normalizeAllowedChatJid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        if (raw.contains("@")) {
            return Jid.of(raw).toSimpleJid();
        }

        var normalized = raw.replace("+", "").replaceAll("\\s+", "");
        return Jid.of(normalized, JidServer.whatsapp()).toSimpleJid();
    }


    private static Optional<String> tryExtractText(ChatMessageInfo info) {
        var container = info.message();
        if (container == null) {
            return Optional.empty();
        }

        var content = container.content();
        if (content instanceof TextMessage textMessage) {
            var text = textMessage.text();
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.trim());
        }

        return Optional.empty();
    }
}

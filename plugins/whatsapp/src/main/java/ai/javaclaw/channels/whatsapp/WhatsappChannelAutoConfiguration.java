package ai.javaclaw.channels.whatsapp;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.ChannelRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

@AutoConfiguration
@EnableConfigurationProperties(WhatsappProperties.class)
public class WhatsappChannelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent.channels.whatsapp", name = "enabled", havingValue = "true")
    public WhatsappService whatsappService(WhatsappProperties properties) {
        return new WhatsappService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent.channels.whatsapp", name = "enabled", havingValue = "true")
    public WhatsappChannel whatsappChannel(Agent agent,
                                           WhatsappService whatsappService,
                                           WhatsappProperties properties,
                                           ChannelRegistry channelRegistry) {
        return new WhatsappChannel(whatsappService, properties, agent, channelRegistry);
    }
}

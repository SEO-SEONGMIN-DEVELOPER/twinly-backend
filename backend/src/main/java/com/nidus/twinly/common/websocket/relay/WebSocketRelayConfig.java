package com.nidus.twinly.common.websocket.relay;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class WebSocketRelayConfig {

    @Bean
    public RedisSerializer<WebSocketRelayMessage> webSocketRelaySerializer(JsonMapper jsonMapper) {
        return new JacksonJsonRedisSerializer<>(jsonMapper, WebSocketRelayMessage.class);
    }

    @Bean
    public RedisTemplate<String, WebSocketRelayMessage> webSocketRelayRedisTemplate(
            RedisConnectionFactory connectionFactory,
            RedisSerializer<WebSocketRelayMessage> webSocketRelaySerializer
    ) {

        RedisTemplate<String, WebSocketRelayMessage> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setValueSerializer(webSocketRelaySerializer);

        return template;
    }

    @Bean
    public RedisMessageListenerContainer webSocketRelayListenerContainer(
            RedisConnectionFactory connectionFactory,
            WebSocketRelayDispatcher webSocketRelayDispatcher,
            @Qualifier("webSocketRelayTaskExecutor") ThreadPoolTaskExecutor webSocketRelayTaskExecutor
    ) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(webSocketRelayTaskExecutor);
        container.addMessageListener(webSocketRelayDispatcher, new ChannelTopic(WebSocketRelayPublisher.CHANNEL));

        return container;
    }

    @Bean(defaultCandidate = false)
    public ThreadPoolTaskExecutor webSocketRelayTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("ws-relay-");

        return executor;
    }
}

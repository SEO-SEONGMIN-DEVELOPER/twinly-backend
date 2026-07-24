package com.nidus.twinly.common.websocket.config;

import com.nidus.twinly.common.websocket.handshake.ConnectionTicketHandshakeHandler;
import com.nidus.twinly.common.websocket.interceptor.ConnectionTicketHandshakeInterceptor;
import com.nidus.twinly.common.websocket.interceptor.WebSocketFrameValidationInterceptor;
import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_INTERVAL_MS = 25_000L;
    private static final int MAX_MESSAGE_BYTES = 64 * 1024;

    private final ConnectionService connectionService;
    private final WebSocketFrameValidationInterceptor frameValidationInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(frameValidationInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/v1/")
                .addInterceptors(new ConnectionTicketHandshakeInterceptor(connectionService, ConnectionType.WS))
                .setHandshakeHandler(new ConnectionTicketHandshakeHandler());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_MESSAGE_BYTES);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(10 * 1000);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BYTES);

        return container;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS})
                .setTaskScheduler(webSocketHeartbeatTaskScheduler());

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Bean
    public TaskScheduler webSocketHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();

        return scheduler;
    }
}

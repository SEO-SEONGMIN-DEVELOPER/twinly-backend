package com.nidus.twinly.common.websocket.config;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "websocket")
public record WebSocketProperties(
        Duration heartbeatInterval
) {

    private static final Duration ALB_IDLE_TIMEOUT = Duration.ofSeconds(60);

    public WebSocketProperties {
        RequiredProperty.requirePositive("websocket.heartbeat-interval", heartbeatInterval);

        if (heartbeatInterval.compareTo(ALB_IDLE_TIMEOUT) >= 0) {
            throw new IllegalStateException("websocket.heartbeat-interval 는 ALB idle timeout(" + ALB_IDLE_TIMEOUT.toSeconds() + "초)보다 짧아야 합니다.");
        }
    }
}

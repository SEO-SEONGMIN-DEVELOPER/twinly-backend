package com.nidus.twinly.common.websocket.relay;

import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRelayPublisher {

    public static final String CHANNEL = "ws:relay";

    private final RedisTemplate<String, WebSocketRelayMessage> webSocketRelayRedisTemplate;

    public void publishToUser(String userId, String destination, WebSocketEventBody<?> body) {
        publish(WebSocketRelayMessage.toUser(userId, destination, body));
    }

    public void publishToAll(String destination, WebSocketEventBody<?> body) {
        publish(WebSocketRelayMessage.toAll(destination, body));
    }

    private void publish(WebSocketRelayMessage message) {
        try {
            webSocketRelayRedisTemplate.convertAndSend(CHANNEL, message);
        } catch (RuntimeException e) {
            log.error("웹소켓 이벤트 전파에 실패했습니다. eventId={}, type={}",
                    message.body().eventId(), message.body().type(), e);
        }
    }
}

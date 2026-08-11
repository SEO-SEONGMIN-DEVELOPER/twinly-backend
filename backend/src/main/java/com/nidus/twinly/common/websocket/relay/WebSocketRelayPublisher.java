package com.nidus.twinly.common.websocket.relay;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.websocket.domain.WebSocketErrorCode;
import com.nidus.twinly.common.websocket.dto.WebSocketResponseBody;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRelayPublisher {

    public static final String CHANNEL = "ws:relay";

    private static final String BODY_KIND = "bodyKind";
    private static final String BODY_TYPE = "bodyType";

    private final RedisTemplate<String, WebSocketRelayMessage> webSocketRelayRedisTemplate;

    public void publishToUser(String userId, String destination, WebSocketResponseBody body) {
        publish(WebSocketRelayMessage.toUser(userId, destination, body));
    }

    public void publishToAll(String destination, WebSocketResponseBody body) {
        publish(WebSocketRelayMessage.toAll(destination, body));
    }

    private void publish(WebSocketRelayMessage message) {
        try {
            webSocketRelayRedisTemplate.convertAndSend(CHANNEL, message);
        } catch (RuntimeException e) {
            ErrorLog.error(log, WebSocketErrorCode.RELAY_PUBLISH_FAILED.name(), null, e)
                    .addKeyValue(BODY_KIND, message.body().kind())
                    .addKeyValue(BODY_TYPE, message.body().type())
                    .log("웹소켓 메시지 전파에 실패했습니다");
        }
    }
}

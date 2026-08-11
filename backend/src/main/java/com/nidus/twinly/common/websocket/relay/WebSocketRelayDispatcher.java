package com.nidus.twinly.common.websocket.relay;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.websocket.domain.WebSocketErrorCode;
import com.nidus.twinly.common.websocket.sender.WebSocketLocalSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRelayDispatcher implements MessageListener {

    private final RedisSerializer<WebSocketRelayMessage> webSocketRelaySerializer;
    private final WebSocketLocalSender webSocketLocalSender;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            WebSocketRelayMessage relayMessage = webSocketRelaySerializer.deserialize(message.getBody());
            if (relayMessage == null) {
                return;
            }

            if (relayMessage.userId() != null) {
                webSocketLocalSender.sendToUser(relayMessage.userId(), relayMessage.destination(), relayMessage.body());
                return;
            }

            webSocketLocalSender.sendToAll(relayMessage.destination(), relayMessage.body());
        } catch (RuntimeException e) {
            ErrorLog.warn(log, WebSocketErrorCode.RELAY_RECEIVE_FAILED.name(), null, e)
                    .log("웹소켓 이벤트 수신 처리에 실패했습니다");
        }
    }
}

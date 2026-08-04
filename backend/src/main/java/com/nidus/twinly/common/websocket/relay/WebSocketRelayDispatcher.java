package com.nidus.twinly.common.websocket.relay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRelayDispatcher implements MessageListener {

    private final RedisSerializer<WebSocketRelayMessage> webSocketRelaySerializer;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            WebSocketRelayMessage relayMessage = webSocketRelaySerializer.deserialize(message.getBody());
            if (relayMessage == null) {
                return;
            }

            if (relayMessage.userId() != null) {
                messagingTemplate.convertAndSendToUser(
                        relayMessage.userId(), relayMessage.destination(), relayMessage.body());
                return;
            }

            simpUserRegistry.getUsers().forEach(user -> messagingTemplate.convertAndSendToUser(
                    user.getName(), relayMessage.destination(), relayMessage.body()));
        } catch (RuntimeException e) {
            log.warn("웹소켓 이벤트 수신 처리에 실패했습니다.", e);
        }
    }
}

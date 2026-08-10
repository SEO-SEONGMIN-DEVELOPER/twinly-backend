package com.nidus.twinly.common.websocket.sender;

import com.nidus.twinly.common.websocket.dto.WebSocketResponseBody;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketLocalSender {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public void sendToUser(String userId, String destination, WebSocketResponseBody body) {
        messagingTemplate.convertAndSendToUser(userId, destination, body);
    }

    public void sendToAll(String destination, WebSocketResponseBody body) {
        simpUserRegistry.getUsers()
                .forEach(user -> messagingTemplate.convertAndSendToUser(user.getName(), destination, body));
    }
}

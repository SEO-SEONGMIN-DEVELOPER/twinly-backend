package com.nidus.twinly.common.websocket.notifier;

import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.connection.dto.websocket.ConnectionDrainingPayload;
import com.nidus.twinly.common.websocket.dto.WebSocketResponseBody;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConnectionControlNotifier {

    private static final String CONTROL_DESTINATION = "/queue/connection/control";

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public void notifyDraining(ConnectionDrainingReason reason, Long retryAfterMs) {
        WebSocketResponseBody body = WebSocketResponseBody.event(
                WebSocketBodyType.CONNECTION_DRAINING,
                new ConnectionDrainingPayload(reason, retryAfterMs));

        simpUserRegistry.getUsers().forEach(user ->
                messagingTemplate.convertAndSendToUser(user.getName(), CONTROL_DESTINATION, body));
    }
}

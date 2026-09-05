package com.nidus.twinly.common.websocket.sender;

import com.nidus.twinly.common.logging.TraceContext;
import com.nidus.twinly.common.websocket.dto.WebSocketResponseBody;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
public class WebSocketLocalSender {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public WebSocketLocalSender(@Lazy SimpMessagingTemplate messagingTemplate,
                                @Lazy SimpUserRegistry simpUserRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
    }

    public void sendToUser(String userId, String destination, WebSocketResponseBody body) {
        messagingTemplate.convertAndSendToUser(userId, destination, body, traceHeaders());
    }

    public void sendToAll(String destination, WebSocketResponseBody body) {
        simpUserRegistry.getUsers()
                .forEach(user -> messagingTemplate.convertAndSendToUser(user.getName(), destination, body, traceHeaders()));
    }

    private MessageHeaders traceHeaders() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        String traceId = TraceContext.currentTraceId();

        if (traceId != null) {
            accessor.setHeader(TraceContext.TRACE_ID, traceId);
        }
        accessor.setLeaveMutable(true);

        return accessor.getMessageHeaders();
    }
}

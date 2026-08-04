package com.nidus.twinly.common.websocket.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebSocketOutboundFailureInterceptor implements ExecutorChannelInterceptor {

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        if (ex == null) {
            return;
        }

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);

        log.warn("웹소켓 전송에 실패했습니다. sessionId={}, destination={}",
                accessor.getSessionId(), accessor.getDestination(), ex);
    }
}

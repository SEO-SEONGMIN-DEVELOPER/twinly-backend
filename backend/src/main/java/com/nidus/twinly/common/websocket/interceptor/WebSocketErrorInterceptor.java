package com.nidus.twinly.common.websocket.interceptor;

import com.nidus.twinly.common.logging.Actor;
import com.nidus.twinly.common.websocket.domain.WebSocketErrorCode;
import com.nidus.twinly.common.websocket.handshake.WebSocketUserPrincipal;
import com.nidus.twinly.common.logging.ErrorLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Slf4j
@Component
public class WebSocketErrorInterceptor implements ExecutorChannelInterceptor {

    private static final String SESSION_ID = "sessionId";
    private static final String DESTINATION = "destination";

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (ex == null) {
            return;
        }

        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);

        ErrorLog.warn(log, WebSocketErrorCode.FRAME_REJECTED.name(), actorOf(headers), ex)
                .addKeyValue(SESSION_ID, headers.getSessionId())
                .addKeyValue(DESTINATION, headers.getDestination())
                .log("웹소켓 프레임 검증에 실패했습니다");
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        if (ex == null) {
            return;
        }

        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);

        ErrorLog.warn(log, WebSocketErrorCode.SEND_FAILED.name(), actorOf(headers), ex)
                .addKeyValue(SESSION_ID, headers.getSessionId())
                .addKeyValue(DESTINATION, headers.getDestination())
                .log("웹소켓 전송에 실패했습니다");
    }

    private String actorOf(SimpMessageHeaderAccessor headers) {
        Principal user = headers.getUser();
        return user instanceof WebSocketUserPrincipal principal ? Actor.user(principal.userId()) : null;
    }
}

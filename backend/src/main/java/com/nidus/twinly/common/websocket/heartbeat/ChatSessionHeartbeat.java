package com.nidus.twinly.common.websocket.heartbeat;

import com.nidus.twinly.common.websocket.registry.ChatSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionHeartbeat {

    private static final Duration PONG_TIMEOUT = Duration.ofSeconds(60);

    private final ChatSessionRegistry chatSessionRegistry;

    @Scheduled(fixedDelay = 30000)
    public void ping() {
        for (Set<WebSocketSession> userSessions : chatSessionRegistry.getAllSessions().values()) {
            for (WebSocketSession session : userSessions) {
                if (isTimedOut(session)) {
                    closeSession(session);
                    continue;
                }
                sendPing(session);
            }
        }
    }

    private void sendPing(WebSocketSession session) {
        try {
            session.sendMessage(new PingMessage());
        } catch (Exception e) {
            log.warn("ping 전송 실패: sessionId={}", session.getId(), e);
        }
    }

    private boolean isTimedOut(WebSocketSession session) {
        Instant lastPongAt = chatSessionRegistry.getLastPongAt(session);
        return lastPongAt == null || lastPongAt.isBefore(Instant.now().minus(PONG_TIMEOUT));
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close();
        } catch (Exception e) {
            log.warn("세션 종료 실패: sessionId={}", session.getId(), e);
        }
    }
}
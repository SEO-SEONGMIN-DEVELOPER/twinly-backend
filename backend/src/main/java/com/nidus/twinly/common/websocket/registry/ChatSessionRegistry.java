package com.nidus.twinly.common.websocket.registry;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastPongAt = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        lastPongAt.put(session.getId(), Instant.now());
    }

    public void unregister(Long userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (key, userSessions) -> {
            userSessions.remove(session);
            return userSessions.isEmpty() ? null : userSessions;
        });

        lastPongAt.remove(session.getId());
    }

    public Set<WebSocketSession> get(Long userId) {
        return sessions.getOrDefault(userId, Set.of());
    }

    public Map<Long, Set<WebSocketSession>> getAllSessions() {
        return sessions;
    }

    public void markPong(WebSocketSession session) {
        lastPongAt.put(session.getId(), Instant.now());
    }

    public Instant getLastPongAt(WebSocketSession session) {
        return lastPongAt.get(session.getId());
    }
}
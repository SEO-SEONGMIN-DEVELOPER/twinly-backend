package com.nidus.twinly.common.websocket.relay;

import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;

public record WebSocketRelayMessage(
        String userId,
        String destination,
        WebSocketEventBody<?> body
) {

    public static WebSocketRelayMessage toUser(String userId, String destination, WebSocketEventBody<?> body) {
        return new WebSocketRelayMessage(userId, destination, body);
    }

    public static WebSocketRelayMessage toAll(String destination, WebSocketEventBody<?> body) {
        return new WebSocketRelayMessage(null, destination, body);
    }
}

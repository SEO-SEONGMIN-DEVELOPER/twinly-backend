package com.nidus.twinly.common.websocket.relay;

import com.nidus.twinly.common.websocket.dto.WebSocketResponseBody;

public record WebSocketRelayMessage(
        String userId,
        String destination,
        WebSocketResponseBody body,
        String traceId
) {

    public static WebSocketRelayMessage toUser(String userId, String destination, WebSocketResponseBody body,
                                               String traceId) {
        return new WebSocketRelayMessage(userId, destination, body, traceId);
    }

    public static WebSocketRelayMessage toAll(String destination, WebSocketResponseBody body, String traceId) {
        return new WebSocketRelayMessage(null, destination, body, traceId);
    }
}

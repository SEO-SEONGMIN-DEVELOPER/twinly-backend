package com.nidus.twinly.common.websocket.handshake;

import java.security.Principal;

public record WebSocketUserPrincipal(
        Long userId
) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
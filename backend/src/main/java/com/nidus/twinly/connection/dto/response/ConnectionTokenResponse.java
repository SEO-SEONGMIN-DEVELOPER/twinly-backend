package com.nidus.twinly.connection.dto.response;

import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.dto.result.ConnectionTokenResult;

import java.time.Instant;
import java.util.UUID;

public record ConnectionTokenResponse(
        UUID ticket,
        ConnectionType connectionType,
        Instant expiresAt
) {

    public static ConnectionTokenResponse from(ConnectionTokenResult result) {
        return new ConnectionTokenResponse(result.ticket(), result.connectionType(), result.expiresAt());
    }
}

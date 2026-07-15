package com.nidus.twinly.connection.dto.result;

import com.nidus.twinly.connection.domain.ConnectionType;

import java.time.Instant;
import java.util.UUID;

public record ConnectionTokenResult(
        UUID ticket,
        ConnectionType connectionType,
        Instant expiresAt
) {
}

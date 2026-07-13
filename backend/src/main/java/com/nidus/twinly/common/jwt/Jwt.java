package com.nidus.twinly.common.jwt;

import java.time.Instant;

public record Jwt(
        String value,
        Instant expiresAt
) {
}
package com.nidus.twinly.anon.dto.result;

import java.time.Instant;
import java.util.UUID;

public record AnonStartResult(
        UUID anonSessionToken,
        Instant expiresAt
) {
}
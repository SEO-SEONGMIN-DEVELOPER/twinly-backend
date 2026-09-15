package com.nidus.twinly.auth.dto.result;

import java.time.Instant;

public record AuthIdentityPrepareResult(
        String authUrl,
        Instant expiresAt,
        String returnUrl,
        String closeUrl
) {
}

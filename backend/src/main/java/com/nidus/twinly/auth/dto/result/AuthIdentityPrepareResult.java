package com.nidus.twinly.auth.dto.result;

import java.time.Instant;

public record AuthIdentityPrepareResult(
        String identityVerificationId,
        Instant expiresAt
) {
}

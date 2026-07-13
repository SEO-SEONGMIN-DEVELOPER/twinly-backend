package com.nidus.twinly.auth.dto.result;

import java.time.Instant;
import java.util.UUID;

public record AuthSmsVerifyResult(
        UUID smsVerifiedToken,
        Instant expiresAt
) {
}

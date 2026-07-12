package com.nidus.twinly.auth.dto.result;

import java.time.Instant;
import java.util.UUID;

public record AuthSmsSendResult(
        UUID smsVerificationToken,
        Instant expiresAt
) {
}

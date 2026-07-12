package com.nidus.twinly.auth.dto.result;

import java.time.Instant;

public record AuthEmailSendResult(
        String emailVerificationToken,
        Instant expiresAt
) {
}

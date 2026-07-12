package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthEmailSendResult;

import java.time.Instant;
import java.util.UUID;

public record AuthEmailSendResponse(
        UUID emailVerificationToken,
        Instant expiresAt
) {

    public static AuthEmailSendResponse from(AuthEmailSendResult result) {
        return new AuthEmailSendResponse(result.emailVerificationToken(), result.expiresAt());
    }
}

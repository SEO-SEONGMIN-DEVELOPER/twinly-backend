package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthEmailSendResult;

import java.time.Instant;

public record AuthEmailSendResponse(
        String emailVerificationToken,
        Instant expiresAt
) {

    public static AuthEmailSendResponse from(AuthEmailSendResult result) {
        return new AuthEmailSendResponse(result.emailVerificationToken(), result.expiresAt());
    }
}

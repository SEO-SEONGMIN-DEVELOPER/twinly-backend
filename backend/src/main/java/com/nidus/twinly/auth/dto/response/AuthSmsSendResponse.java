package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthSmsSendResult;

import java.time.Instant;
import java.util.UUID;

public record AuthSmsSendResponse(
        UUID smsVerificationToken,
        Instant expiresAt
) {

    public static AuthSmsSendResponse from(AuthSmsSendResult result) {
        return new AuthSmsSendResponse(result.smsVerificationToken(), result.expiresAt());
    }
}

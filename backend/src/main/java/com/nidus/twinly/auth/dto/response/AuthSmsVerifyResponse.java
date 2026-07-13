package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthSmsVerifyResult;

import java.time.Instant;
import java.util.UUID;

public record AuthSmsVerifyResponse(
        UUID smsVerifiedToken,
        Instant expiresAt
) {

    public static AuthSmsVerifyResponse from(AuthSmsVerifyResult result) {
        return new AuthSmsVerifyResponse(result.smsVerifiedToken(), result.expiresAt());
    }
}

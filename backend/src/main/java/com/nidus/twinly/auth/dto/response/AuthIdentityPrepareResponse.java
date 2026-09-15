package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthIdentityPrepareResult;

import java.time.Instant;

public record AuthIdentityPrepareResponse(
        String authUrl,
        Instant expiresAt,
        String returnUrl,
        String closeUrl
) {

    public static AuthIdentityPrepareResponse from(AuthIdentityPrepareResult result) {
        return new AuthIdentityPrepareResponse(result.authUrl(), result.expiresAt(), result.returnUrl(), result.closeUrl());
    }
}

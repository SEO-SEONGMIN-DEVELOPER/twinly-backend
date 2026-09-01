package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthIdentityPrepareResult;

import java.time.Instant;

public record AuthIdentityPrepareResponse(
        String identityVerificationId,
        Instant expiresAt
) {

    public static AuthIdentityPrepareResponse from(AuthIdentityPrepareResult result) {
        return new AuthIdentityPrepareResponse(result.identityVerificationId(), result.expiresAt());
    }
}

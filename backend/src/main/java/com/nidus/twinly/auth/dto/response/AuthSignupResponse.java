package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthSignupResult;

import java.time.Instant;

public record AuthSignupResponse(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt
) {

    public static AuthSignupResponse from(AuthSignupResult result) {
        return new AuthSignupResponse(result.accessToken(), result.accessExpiresAt(), result.refreshToken(), result.refreshExpiresAt());
    }
}

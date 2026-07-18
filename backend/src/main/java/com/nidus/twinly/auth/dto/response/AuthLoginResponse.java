package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthTokenResult;

import java.time.Instant;

public record AuthLoginResponse(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt
) {

    public static AuthLoginResponse from(AuthTokenResult result) {
        return new AuthLoginResponse(result.accessToken(), result.accessExpiresAt(), result.refreshToken(), result.refreshExpiresAt());
    }
}

package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthRefreshResult;

import java.time.Instant;

public record AuthRefreshResponse(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt
) {

    public static AuthRefreshResponse from(AuthRefreshResult result) {
        return new AuthRefreshResponse(result.accessToken(), result.accessExpiresAt(), result.refreshToken(), result.refreshExpiresAt());
    }
}

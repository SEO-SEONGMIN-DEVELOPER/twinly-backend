package com.nidus.twinly.auth.dto.result;

import java.time.Instant;

public record AuthRefreshResult(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt
) {
}

package com.nidus.twinly.common.presign;

import java.time.Instant;

public record PhotoPresignResult(
        String uploadUrl,
        String key,
        String method,
        RequiredHeaders requiredHeaders,
        Integer maxBytes,
        Instant expiresAt
) {
}
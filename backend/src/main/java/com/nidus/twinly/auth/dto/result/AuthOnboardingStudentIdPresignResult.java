package com.nidus.twinly.auth.dto.result;

import com.nidus.twinly.common.presign.RequiredHeaders;

import java.time.Instant;

public record AuthOnboardingStudentIdPresignResult(
        String uploadUrl,
        String key,
        String method,
        RequiredHeaders requiredHeaders,
        Integer maxBytes,
        Instant expiresAt
) {
}

package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.common.presign.RequiredHeaders;

import java.time.Instant;

public record MeSchoolVerificationStudentIdPresignResult(
        String uploadUrl,
        String key,
        String method,
        RequiredHeaders requiredHeaders,
        Integer maxBytes,
        Instant expiresAt
) {
}

package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthOnboardingStudentIdPresignResult;
import com.nidus.twinly.common.presign.RequiredHeaders;

import java.time.Instant;

public record AuthOnboardingStudentIdPresignResponse(
        String uploadUrl,
        String key,
        String method,
        RequiredHeaders requiredHeaders,
        Integer maxBytes,
        Instant expiresAt
) {

    public static AuthOnboardingStudentIdPresignResponse from(AuthOnboardingStudentIdPresignResult result) {
        return new AuthOnboardingStudentIdPresignResponse(
                result.uploadUrl(),
                result.key(),
                result.method(),
                result.requiredHeaders(),
                result.maxBytes(),
                result.expiresAt()
        );
    }
}

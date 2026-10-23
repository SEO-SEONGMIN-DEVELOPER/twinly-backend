package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.me.dto.result.MeSchoolVerificationStudentIdPresignResult;

import java.time.Instant;

public record MeSchoolVerificationStudentIdPresignResponse(
        String uploadUrl,
        String key,
        String method,
        RequiredHeaders requiredHeaders,
        Integer maxBytes,
        Instant expiresAt
) {

    public static MeSchoolVerificationStudentIdPresignResponse from(MeSchoolVerificationStudentIdPresignResult result) {
        return new MeSchoolVerificationStudentIdPresignResponse(
                result.uploadUrl(),
                result.key(),
                result.method(),
                result.requiredHeaders(),
                result.maxBytes(),
                result.expiresAt()
        );
    }
}

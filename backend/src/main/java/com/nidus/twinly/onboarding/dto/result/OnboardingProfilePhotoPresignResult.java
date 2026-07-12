package com.nidus.twinly.onboarding.dto.result;

import com.nidus.twinly.common.presign.RequiredHeaders;

import java.time.Instant;

public record OnboardingProfilePhotoPresignResult(
        String uploadUrl,
        String key,
        String method,
        RequiredHeaders requiredHeaders,
        Integer maxBytes,
        Instant expiresAt
) {
}

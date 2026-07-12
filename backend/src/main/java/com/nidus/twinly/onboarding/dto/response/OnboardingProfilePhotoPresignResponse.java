package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoPresignResult;

public record OnboardingProfilePhotoPresignResponse(
        String uploadUrl,
        String key,
        String method,
        RequiredHeaders requiredHeaders,
        Integer maxBytes,
        Integer expiresInSeconds
) {

    public static OnboardingProfilePhotoPresignResponse from(OnboardingProfilePhotoPresignResult result) {
        return new OnboardingProfilePhotoPresignResponse(result.uploadUrl(), result.key(), result.method(), result.requiredHeaders(), result.maxBytes(), result.expiresInSeconds());
    }
}

package com.nidus.twinly.onboarding.dto.result;

import com.nidus.twinly.common.presign.RequiredHeaders;

public record OnboardingProfilePhotoPresignResult(String uploadUrl,
                                                 String key,
                                                 String method,
                                                 RequiredHeaders requiredHeaders,
                                                 Integer maxBytes,
                                                 Integer expiresInSeconds) {
}

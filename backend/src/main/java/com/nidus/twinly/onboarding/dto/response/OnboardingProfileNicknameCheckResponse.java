package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingProfileNicknameCheckResult;

public record OnboardingProfileNicknameCheckResponse(Boolean isAvailable) {

    public static OnboardingProfileNicknameCheckResponse from(OnboardingProfileNicknameCheckResult result) {
        return new OnboardingProfileNicknameCheckResponse(result.isAvailable());
    }
}

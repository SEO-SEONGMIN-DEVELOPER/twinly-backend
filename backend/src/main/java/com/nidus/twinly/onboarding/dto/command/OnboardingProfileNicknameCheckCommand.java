package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingProfileNicknameCheckRequest;

public record OnboardingProfileNicknameCheckCommand(String nickname) {

    public static OnboardingProfileNicknameCheckCommand from(OnboardingProfileNicknameCheckRequest request) {
        return new OnboardingProfileNicknameCheckCommand(request.nickname());
    }
}

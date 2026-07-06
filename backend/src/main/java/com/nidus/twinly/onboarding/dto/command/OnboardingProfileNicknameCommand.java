package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingProfileNicknameRequest;

public record OnboardingProfileNicknameCommand(String nickname) {

    public static OnboardingProfileNicknameCommand from(OnboardingProfileNicknameRequest request) {
        return new OnboardingProfileNicknameCommand(request.nickname());
    }
}

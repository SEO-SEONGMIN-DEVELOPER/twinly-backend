package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingHeightRequest;

public record OnboardingHeightCommand(Integer height) {

    public static OnboardingHeightCommand from(OnboardingHeightRequest request) {
        return new OnboardingHeightCommand(request.height());
    }
}

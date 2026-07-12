package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingInterestsRequest;

import java.util.List;

public record OnboardingInterestsCommand(
        List<String> interests
) {

    public static OnboardingInterestsCommand from(OnboardingInterestsRequest request) {
        return new OnboardingInterestsCommand(request.interests());
    }
}

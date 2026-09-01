package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingNameRequest;

public record OnboardingNameCommand(
        String familyName,
        String givenName
) {

    public static OnboardingNameCommand from(OnboardingNameRequest request) {
        return new OnboardingNameCommand(request.familyName(), request.givenName());
    }
}

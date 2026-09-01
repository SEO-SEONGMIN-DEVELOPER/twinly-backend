package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingAffiliationNumberRequest;

public record OnboardingAffiliationNumberCommand(
        String affiliationNumber
) {

    public static OnboardingAffiliationNumberCommand from(OnboardingAffiliationNumberRequest request) {
        return new OnboardingAffiliationNumberCommand(request.affiliationNumber());
    }
}

package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingAffiliationRequest;

public record OnboardingAffiliationCommand(
        String affiliation
) {

    public static OnboardingAffiliationCommand from(OnboardingAffiliationRequest request) {
        return new OnboardingAffiliationCommand(request.affiliation());
    }
}

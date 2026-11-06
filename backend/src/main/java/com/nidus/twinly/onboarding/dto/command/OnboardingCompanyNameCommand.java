package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingCompanyNameRequest;

public record OnboardingCompanyNameCommand(
        String companyName
) {

    public static OnboardingCompanyNameCommand from(OnboardingCompanyNameRequest request) {
        return new OnboardingCompanyNameCommand(request.companyName());
    }
}

package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingAffiliationsResult;

import java.util.List;

public record OnboardingAffiliationsResponse(
        List<String> affiliations
) {

    public static OnboardingAffiliationsResponse from(OnboardingAffiliationsResult result) {
        return new OnboardingAffiliationsResponse(result.affiliations());
    }
}

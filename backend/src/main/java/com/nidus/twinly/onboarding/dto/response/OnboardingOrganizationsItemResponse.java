package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingOrganizationsItemResult;

import java.util.List;

public record OnboardingOrganizationsItemResponse(
        String organizationName,
        List<String> domains
) {

    public static OnboardingOrganizationsItemResponse from(OnboardingOrganizationsItemResult result) {
        return new OnboardingOrganizationsItemResponse(result.organizationName(), result.domains());
    }
}

package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingOrganizationsResult;

import java.util.List;

public record OnboardingOrganizationsResponse(
        List<OnboardingOrganizationsItemResponse> organizations
) {

    public static OnboardingOrganizationsResponse from(OnboardingOrganizationsResult result) {
        return new OnboardingOrganizationsResponse(
                result.organizations().stream().map(OnboardingOrganizationsItemResponse::from).toList()
        );
    }
}

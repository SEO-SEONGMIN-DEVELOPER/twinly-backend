package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingStudentIdOrganizationsItemResult;

public record OnboardingStudentIdOrganizationsItemResponse(
        String organizationName
) {

    public static OnboardingStudentIdOrganizationsItemResponse from(OnboardingStudentIdOrganizationsItemResult result) {
        return new OnboardingStudentIdOrganizationsItemResponse(result.organizationName());
    }
}

package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingSchoolsResult;

import java.util.List;

public record OnboardingSchoolsResponse(
        List<OnboardingSchoolsItemResponse> schools
) {

    public static OnboardingSchoolsResponse from(OnboardingSchoolsResult result) {
        return new OnboardingSchoolsResponse(
                result.schools().stream().map(OnboardingSchoolsItemResponse::from).toList()
        );
    }
}

package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingSchoolsItemResult;

import java.util.List;

public record OnboardingSchoolsItemResponse(
        String schoolName,
        List<String> domains
) {

    public static OnboardingSchoolsItemResponse from(OnboardingSchoolsItemResult result) {
        return new OnboardingSchoolsItemResponse(result.schoolName(), result.domains());
    }
}

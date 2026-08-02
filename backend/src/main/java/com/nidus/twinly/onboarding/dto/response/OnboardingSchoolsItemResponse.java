package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingSchoolsItemResult;

public record OnboardingSchoolsItemResponse(
        String schoolName,
        String domain
) {

    public static OnboardingSchoolsItemResponse from(OnboardingSchoolsItemResult result) {
        return new OnboardingSchoolsItemResponse(result.schoolName(), result.domain());
    }
}

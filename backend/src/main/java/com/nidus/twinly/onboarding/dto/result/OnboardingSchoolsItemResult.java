package com.nidus.twinly.onboarding.dto.result;

import java.util.List;

public record OnboardingSchoolsItemResult(
        String schoolName,
        List<String> domains
) {
}

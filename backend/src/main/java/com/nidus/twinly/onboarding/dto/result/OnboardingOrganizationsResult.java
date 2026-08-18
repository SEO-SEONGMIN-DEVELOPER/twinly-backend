package com.nidus.twinly.onboarding.dto.result;

import java.util.List;

public record OnboardingOrganizationsResult(
        List<OnboardingOrganizationsItemResult> organizations
) {
}

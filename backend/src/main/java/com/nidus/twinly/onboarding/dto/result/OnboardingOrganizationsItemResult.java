package com.nidus.twinly.onboarding.dto.result;

import java.util.List;

public record OnboardingOrganizationsItemResult(
        String organizationName,
        List<String> domains
) {
}

package com.nidus.twinly.onboarding.dto.request;

import java.util.List;

public record OnboardingInterestsRequest(
        List<String> interests
) {
}

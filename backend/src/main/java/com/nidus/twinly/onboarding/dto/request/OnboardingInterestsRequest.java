package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OnboardingInterestsRequest(
        @NotNull List<String> interests
) {
}

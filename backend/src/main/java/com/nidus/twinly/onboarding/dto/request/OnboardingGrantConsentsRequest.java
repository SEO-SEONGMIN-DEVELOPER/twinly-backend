package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OnboardingGrantConsentsRequest(
        @NotNull @Valid List<OnboardingGrantConsentsItemRequest> grants
) {
}

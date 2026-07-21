package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotNull;

public record OnboardingGrantConsentsItemRequest(
        @NotNull Long policyId,
        @NotNull Integer version
) {
}

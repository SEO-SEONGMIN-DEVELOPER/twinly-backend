package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OnboardingRevokeConsentsItemRequest(
        @NotBlank String policyId,
        @NotNull Integer version
) {
}

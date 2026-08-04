package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OnboardingRevokeConsentsItemRequest(
        @NotBlank String policyId,
        @NotBlank String version
) {
}

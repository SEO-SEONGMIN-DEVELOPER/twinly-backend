package com.nidus.twinly.onboarding.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OnboardingRevokeConsentsItemRequest(
        @NotBlank String policyId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Integer version
) {
}

package com.nidus.twinly.onboarding.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record OnboardingGrantConsentsItemRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long policyId,
        @NotNull Integer version
) {
}

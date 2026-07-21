package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OnboardingRevokeConsentsRequest(
        @NotNull @Valid List<OnboardingRevokeConsentsItemRequest> grants
) {
}

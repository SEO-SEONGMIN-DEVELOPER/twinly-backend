package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnboardingAffiliationRequest(
        @NotBlank @Size(max = 50) String affiliation
) {
}

package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnboardingAffiliationNumberRequest(
        @NotBlank @Size(max = 50) String affiliationNumber
) {
}

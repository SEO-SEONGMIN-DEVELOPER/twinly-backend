package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnboardingNameRequest(
        @NotBlank @Size(max = 50) String familyName,
        @NotBlank @Size(max = 50) String givenName
) {
}

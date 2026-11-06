package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnboardingCompanyNameRequest(
        @NotBlank @Size(max = 100) String companyName
) {
}

package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OnboardingInterestsRequest(
        @NotNull @Size(max = 50) List<@NotBlank @Size(max = 50) String> interests
) {
}

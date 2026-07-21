package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotNull;
import com.nidus.twinly.common.domain.Gender;

import java.time.LocalDate;

public record OnboardingBasicInfoRequest(
        @NotNull String familyName,
        @NotNull String givenName,
        @NotNull Gender gender,
        @NotNull String affiliation,
        @NotNull String affiliationNumber,
        @NotNull LocalDate birthDate
) {
}

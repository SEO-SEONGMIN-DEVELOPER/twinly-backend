package com.nidus.twinly.onboarding.dto.request;

import com.nidus.twinly.common.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record OnboardingBasicInfoRequest(
        @NotBlank @Size(max = 50) String familyName,
        @NotBlank @Size(max = 50) String givenName,
        @NotNull Gender gender,
        @NotBlank @Size(max = 50) String affiliation,
        @NotBlank @Size(max = 50) String affiliationNumber,
        @NotNull @Past LocalDate birthDate
) {
}

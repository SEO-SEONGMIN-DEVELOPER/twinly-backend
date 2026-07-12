package com.nidus.twinly.onboarding.dto.request;

import com.nidus.twinly.common.domain.Gender;

import java.time.LocalDate;

public record OnboardingBasicInfoRequest(
        String familyName,
        String givenName,
        Gender gender,
        String affiliation,
        String affiliationNumber,
        LocalDate birthDate
) {
}

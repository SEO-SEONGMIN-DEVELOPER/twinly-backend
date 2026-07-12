package com.nidus.twinly.onboarding.dto.request;

public record OnboardingBasicInfoRequest(
        String familyName,
        String givenName,
        String gender,
        String affiliation,
        String affiliationNumber,
        String birthDate
) {
}

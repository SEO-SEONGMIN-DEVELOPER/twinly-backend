package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.onboarding.dto.request.OnboardingBasicInfoRequest;

import java.time.LocalDate;

public record OnboardingBasicInfoCommand(
        String familyName,
        String givenName,
        Gender gender,
        String affiliationNumber,
        LocalDate birthDate
) {

    public static OnboardingBasicInfoCommand from(OnboardingBasicInfoRequest request) {
        return new OnboardingBasicInfoCommand(request.familyName(), request.givenName(), request.gender(), request.affiliationNumber(), request.birthDate());
    }
}

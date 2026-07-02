package com.nidus.twinly.onboarding.dto;

public record OnboardingBasicInfoCommand(String familyName, String givenName, String gender, String affiliation, String affiliationNumber, String experience, String birthDate) {
    public static OnboardingBasicInfoCommand from(OnboardingBasicInfoRequest request) {
        return new OnboardingBasicInfoCommand(request.familyName(), request.givenName(), request.gender(), request.affiliation(), request.affiliationNumber(), request.experience(), request.birthDate());
    }
}
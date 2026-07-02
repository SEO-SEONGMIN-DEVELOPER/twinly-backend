package com.nidus.twinly.onboarding.dto;

public record OnboardingBasicInfoRequest(String familyName, String givenName, String gender, String affiliation, String affiliationNumber, String experience, String birthDate) {
}
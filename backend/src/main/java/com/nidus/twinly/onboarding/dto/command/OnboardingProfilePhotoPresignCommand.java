package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingProfilePhotoPresignRequest;

public record OnboardingProfilePhotoPresignCommand(
        String contentType
) {

    public static OnboardingProfilePhotoPresignCommand from(OnboardingProfilePhotoPresignRequest request) {
        return new OnboardingProfilePhotoPresignCommand(request.contentType());
    }
}

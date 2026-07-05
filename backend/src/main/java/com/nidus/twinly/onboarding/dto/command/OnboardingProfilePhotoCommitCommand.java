package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingProfilePhotoCommitRequest;

public record OnboardingProfilePhotoCommitCommand(String key) {

    public static OnboardingProfilePhotoCommitCommand from(OnboardingProfilePhotoCommitRequest request) {
        return new OnboardingProfilePhotoCommitCommand(request.key());
    }
}

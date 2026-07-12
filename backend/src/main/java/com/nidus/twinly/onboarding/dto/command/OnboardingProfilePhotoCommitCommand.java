package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.onboarding.dto.request.OnboardingProfilePhotoCommitRequest;

public record OnboardingProfilePhotoCommitCommand(
        String key,
        PhotoPosInfo position
) {

    public static OnboardingProfilePhotoCommitCommand from(OnboardingProfilePhotoCommitRequest request) {
        return new OnboardingProfilePhotoCommitCommand(request.key(), request.position());
    }
}
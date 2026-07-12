package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.onboarding.dto.result.OnboardingProfilePhotoCommitResult;

public record OnboardingProfilePhotoCommitResponse(
        String photoUrl,
        PhotoPosInfo position
) {

    public static OnboardingProfilePhotoCommitResponse from(OnboardingProfilePhotoCommitResult result) {
        return new OnboardingProfilePhotoCommitResponse(result.photoUrl(), result.position());
    }
}
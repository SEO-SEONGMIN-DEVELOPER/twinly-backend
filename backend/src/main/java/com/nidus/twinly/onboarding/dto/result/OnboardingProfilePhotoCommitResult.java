package com.nidus.twinly.onboarding.dto.result;

import com.nidus.twinly.common.photo.PhotoPosInfo;

public record OnboardingProfilePhotoCommitResult(
        String photoUrl,
        PhotoPosInfo position
) {
}
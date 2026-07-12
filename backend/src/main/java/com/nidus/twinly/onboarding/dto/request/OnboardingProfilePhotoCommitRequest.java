package com.nidus.twinly.onboarding.dto.request;

import com.nidus.twinly.common.photo.PhotoPosInfo;

public record OnboardingProfilePhotoCommitRequest(
        String key,
        PhotoPosInfo position
) {
}
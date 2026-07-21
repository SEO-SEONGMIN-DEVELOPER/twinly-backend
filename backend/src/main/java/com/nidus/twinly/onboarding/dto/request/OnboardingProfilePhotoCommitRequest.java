package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.nidus.twinly.common.photo.PhotoPosInfo;

public record OnboardingProfilePhotoCommitRequest(
        @NotNull String key,
        @NotNull @Valid PhotoPosInfo position
) {
}
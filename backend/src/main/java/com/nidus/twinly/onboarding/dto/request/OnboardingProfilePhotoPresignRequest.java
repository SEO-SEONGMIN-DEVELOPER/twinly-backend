package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotNull;

public record OnboardingProfilePhotoPresignRequest(
        @NotNull String contentType
) {
}

package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OnboardingProfileNicknameCheckRequest(
        @NotBlank String nickname
) {
}

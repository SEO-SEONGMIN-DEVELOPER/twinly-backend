package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OnboardingProfileNicknameRequest(
        @NotBlank String nickname
) {
}

package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OnboardingAiChatMessageRequest(
        @NotBlank String message,
        @NotNull Integer turnIndex
) {
}

package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.constraints.NotNull;

public record OnboardingAiChatMessageRequest(
        @NotNull String message,
        @NotNull Integer turnIndex
) {
}

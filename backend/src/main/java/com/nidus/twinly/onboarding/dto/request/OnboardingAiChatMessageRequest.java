package com.nidus.twinly.onboarding.dto.request;

public record OnboardingAiChatMessageRequest(
        String message,
        Integer turnIndex
) {
}

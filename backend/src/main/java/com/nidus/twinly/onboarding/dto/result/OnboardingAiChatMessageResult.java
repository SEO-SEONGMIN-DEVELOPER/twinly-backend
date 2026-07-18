package com.nidus.twinly.onboarding.dto.result;

public record OnboardingAiChatMessageResult(
        String message,
        Integer turnIndex,
        Boolean isEnd
) {
}

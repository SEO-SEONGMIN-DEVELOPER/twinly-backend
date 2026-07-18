package com.nidus.twinly.onboarding.dto.result;

public record OnboardingAiChatStartResult(
        String message,
        Integer turnIndex,
        Boolean isEnd
) {
}

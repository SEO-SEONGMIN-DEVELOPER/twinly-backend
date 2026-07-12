package com.nidus.twinly.onboarding.dto.result;

public record OnboardingAiChatStartResult(
        String reply,
        Integer turnIndex,
        Boolean isEnd
) {
}

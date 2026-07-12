package com.nidus.twinly.onboarding.dto.result;

public record OnboardingAiChatMessageResult(
        String reply,
        Integer turnIndex,
        Boolean isEnd
) {
}

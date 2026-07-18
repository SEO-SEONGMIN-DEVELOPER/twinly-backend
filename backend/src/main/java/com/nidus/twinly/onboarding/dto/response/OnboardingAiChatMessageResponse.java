package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatMessageResult;

public record OnboardingAiChatMessageResponse(
        String message,
        Integer turnIndex,
        Boolean isEnd
) {

    public static OnboardingAiChatMessageResponse from(OnboardingAiChatMessageResult result) {
        return new OnboardingAiChatMessageResponse(result.message(), result.turnIndex(), result.isEnd());
    }
}

package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatMessageResult;

public record OnboardingAiChatMessageResponse(
        String reply,
        Integer turnIndex,
        Boolean isEnd
) {

    public static OnboardingAiChatMessageResponse from(OnboardingAiChatMessageResult result) {
        return new OnboardingAiChatMessageResponse(result.reply(), result.turnIndex(), result.isEnd());
    }
}

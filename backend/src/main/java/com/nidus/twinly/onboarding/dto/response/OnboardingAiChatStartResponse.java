package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingAiChatStartResult;

public record OnboardingAiChatStartResponse(
        String reply,
        Integer turnIndex,
        Boolean isEnd
) {

    public static OnboardingAiChatStartResponse from(OnboardingAiChatStartResult result) {
        return new OnboardingAiChatStartResponse(result.reply(), result.turnIndex(), result.isEnd());
    }
}

package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingAiChatMessageRequest;

public record OnboardingAiChatMessageCommand(
        String message,
        Integer turnIndex
) {

    public static OnboardingAiChatMessageCommand from(OnboardingAiChatMessageRequest request) {
        return new OnboardingAiChatMessageCommand(request.message(), request.turnIndex());
    }
}

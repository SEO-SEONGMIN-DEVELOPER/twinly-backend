package com.nidus.twinly.me.dto.result;

public record MeStatusPersonaResult(
        Boolean isSurveyCompleted,
        Boolean isInterestsCompleted,
        Boolean isAiChatCompleted
) {
}

package com.nidus.twinly.me.dto.result;

import java.time.Instant;

public record MeStatusPersonaResult(
        Boolean isSurveyCompleted,
        Boolean isInterestsCompleted,
        Boolean isAiChatCompleted,
        Instant nextUpdatableAt
) {
}

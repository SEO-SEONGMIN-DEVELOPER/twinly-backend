package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeStatusPersonaResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MeStatusPersonaResponse(
        Boolean isSurveyCompleted,
        Boolean isInterestsCompleted,
        Boolean isAiChatCompleted,
        @Schema(nullable = true)
        Instant nextUpdatableAt
) {

    public static MeStatusPersonaResponse from(MeStatusPersonaResult result) {
        return new MeStatusPersonaResponse(
                result.isSurveyCompleted(),
                result.isInterestsCompleted(),
                result.isAiChatCompleted(),
                result.nextUpdatableAt()
        );
    }
}

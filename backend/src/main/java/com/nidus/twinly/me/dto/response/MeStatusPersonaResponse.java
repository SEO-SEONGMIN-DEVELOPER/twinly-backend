package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeStatusPersonaResult;

public record MeStatusPersonaResponse(
        Boolean isSurveyCompleted,
        Boolean isInterestsCompleted,
        Boolean isAiChatCompleted
) {

    public static MeStatusPersonaResponse from(MeStatusPersonaResult result) {
        return new MeStatusPersonaResponse(
                result.isSurveyCompleted(),
                result.isInterestsCompleted(),
                result.isAiChatCompleted()
        );
    }
}

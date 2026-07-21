package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ActivityResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId,
        LocalDate date,
        String version,
        Instant serverNow,
        List<ActivitySceneResponse> scenes,
        List<ActivityQuestionResponse> questions
) {

    public static ActivityResponse from(ActivityResult result) {
        return new ActivityResponse(
                result.userId(),
                result.seasonId(),
                result.date(),
                result.version(),
                result.serverNow(),
                result.scenes().stream().map(ActivitySceneResponse::from).toList(),
                result.questions().stream().map(ActivityQuestionResponse::from).toList()
        );
    }
}

package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityQuestionResult;

import java.time.Instant;
import java.util.List;

public record ActivityQuestionResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        String type,
        Instant time,
        String text,
        List<String> options
) {

    public static ActivityQuestionResponse from(ActivityQuestionResult result) {
        return new ActivityQuestionResponse(result.id(), result.type(), result.time(), result.text(), result.options());
    }
}

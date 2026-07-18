package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.activity.dto.result.ActivityQuestionResult;

import java.time.LocalTime;
import java.util.List;

public record ActivityQuestionResponse(
        Long id,
        String type,
        LocalTime time,
        String text,
        List<String> options
) {

    public static ActivityQuestionResponse from(ActivityQuestionResult result) {
        return new ActivityQuestionResponse(result.id(), result.type(), result.time(), result.text(), result.options());
    }
}

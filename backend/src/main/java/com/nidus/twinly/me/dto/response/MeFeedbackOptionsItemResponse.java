package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeFeedbackOptionsItemResult;

public record MeFeedbackOptionsItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        String label
) {

    public static MeFeedbackOptionsItemResponse from(MeFeedbackOptionsItemResult result) {
        return new MeFeedbackOptionsItemResponse(result.id(), result.label());
    }
}

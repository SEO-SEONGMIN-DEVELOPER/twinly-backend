package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeTendencyQuestionsOptionResult;

public record MeTendencyQuestionsOptionResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        String label
) {

    public static MeTendencyQuestionsOptionResponse from(MeTendencyQuestionsOptionResult result) {
        return new MeTendencyQuestionsOptionResponse(result.id(), result.label());
    }
}

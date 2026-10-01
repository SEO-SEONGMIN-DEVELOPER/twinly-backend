package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeTendencyQuestionsItemResult;

import java.util.List;

public record MeTendencyQuestionsItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        String text,
        List<MeTendencyQuestionsOptionResponse> options
) {

    public static MeTendencyQuestionsItemResponse from(MeTendencyQuestionsItemResult result) {
        return new MeTendencyQuestionsItemResponse(
                result.id(),
                result.text(),
                result.options().stream().map(MeTendencyQuestionsOptionResponse::from).toList()
        );
    }
}

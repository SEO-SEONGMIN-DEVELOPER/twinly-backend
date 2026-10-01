package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeTendencyQuestionsResult;

import java.util.List;

public record MeTendencyQuestionsResponse(
        List<MeTendencyQuestionsItemResponse> questions
) {

    public static MeTendencyQuestionsResponse from(MeTendencyQuestionsResult result) {
        return new MeTendencyQuestionsResponse(
                result.questions().stream().map(MeTendencyQuestionsItemResponse::from).toList()
        );
    }
}

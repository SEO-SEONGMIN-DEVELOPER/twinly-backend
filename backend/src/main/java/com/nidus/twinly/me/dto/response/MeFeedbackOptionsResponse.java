package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeFeedbackOptionsResult;

import java.util.List;

public record MeFeedbackOptionsResponse(
        List<MeFeedbackOptionsItemResponse> options
) {

    public static MeFeedbackOptionsResponse from(MeFeedbackOptionsResult result) {
        return new MeFeedbackOptionsResponse(
                result.options().stream().map(MeFeedbackOptionsItemResponse::from).toList()
        );
    }
}

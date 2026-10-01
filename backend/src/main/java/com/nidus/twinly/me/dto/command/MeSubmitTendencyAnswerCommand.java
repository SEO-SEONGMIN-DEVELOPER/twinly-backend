package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeSubmitTendencyAnswerRequest;

public record MeSubmitTendencyAnswerCommand(
        Long optionId
) {

    public static MeSubmitTendencyAnswerCommand from(MeSubmitTendencyAnswerRequest request) {
        return new MeSubmitTendencyAnswerCommand(request.optionId());
    }
}

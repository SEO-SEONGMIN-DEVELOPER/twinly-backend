package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.common.survey.SurveyAnswerInput;
import com.nidus.twinly.me.dto.request.MeSubmitTwinUpdateSurveyAnswerRequest;

import java.util.UUID;

public record MeSubmitTwinUpdateSurveyAnswerCommand(
        UUID updateId,
        SurveyAnswerInput answer
) {

    public static MeSubmitTwinUpdateSurveyAnswerCommand from(MeSubmitTwinUpdateSurveyAnswerRequest request) {
        return new MeSubmitTwinUpdateSurveyAnswerCommand(request.updateId(), request.answer());
    }
}

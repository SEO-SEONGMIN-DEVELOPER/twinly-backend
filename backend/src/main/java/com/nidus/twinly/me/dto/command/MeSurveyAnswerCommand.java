package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.common.survey.SurveyAnswerInput;
import com.nidus.twinly.me.dto.request.MeSurveyAnswerRequest;

public record MeSurveyAnswerCommand(
        SurveyAnswerInput answer
) {

    public static MeSurveyAnswerCommand from(MeSurveyAnswerRequest request) {
        return new MeSurveyAnswerCommand(request.answer());
    }
}

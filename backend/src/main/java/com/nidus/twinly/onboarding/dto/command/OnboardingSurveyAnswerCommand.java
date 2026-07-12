package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.common.survey.SurveyAnswerInput;
import com.nidus.twinly.onboarding.dto.request.OnboardingSurveyAnswerRequest;

public record OnboardingSurveyAnswerCommand(
        SurveyAnswerInput answer
) {

    public static OnboardingSurveyAnswerCommand from(OnboardingSurveyAnswerRequest request) {
        return new OnboardingSurveyAnswerCommand(request.answer());
    }
}

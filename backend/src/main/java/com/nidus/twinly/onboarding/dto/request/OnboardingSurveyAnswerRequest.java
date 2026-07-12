package com.nidus.twinly.onboarding.dto.request;

import com.nidus.twinly.common.survey.SurveyAnswerInput;

public record OnboardingSurveyAnswerRequest(
        SurveyAnswerInput answer
) {
}

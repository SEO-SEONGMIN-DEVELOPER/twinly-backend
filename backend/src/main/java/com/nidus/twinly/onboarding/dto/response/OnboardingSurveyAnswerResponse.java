package com.nidus.twinly.onboarding.dto.response;

import com.nidus.twinly.onboarding.dto.result.OnboardingSurveyAnswerResult;

public record OnboardingSurveyAnswerResponse(Boolean isCompleted) {
    public static OnboardingSurveyAnswerResponse from(OnboardingSurveyAnswerResult result) {
        return new OnboardingSurveyAnswerResponse(result.isCompleted());
    }
}

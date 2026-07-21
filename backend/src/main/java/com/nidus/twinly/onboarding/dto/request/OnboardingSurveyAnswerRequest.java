package com.nidus.twinly.onboarding.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.nidus.twinly.common.survey.SurveyAnswerInput;

public record OnboardingSurveyAnswerRequest(
        @NotNull @Valid SurveyAnswerInput answer
) {
}

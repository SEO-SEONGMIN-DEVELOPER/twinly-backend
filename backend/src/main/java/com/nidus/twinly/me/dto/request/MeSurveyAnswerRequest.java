package com.nidus.twinly.me.dto.request;

import com.nidus.twinly.common.survey.SurveyAnswerInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MeSurveyAnswerRequest(
        @NotNull @Valid SurveyAnswerInput answer
) {
}

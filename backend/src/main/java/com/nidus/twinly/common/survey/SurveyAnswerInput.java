package com.nidus.twinly.common.survey;

import jakarta.validation.constraints.NotNull;

public record SurveyAnswerInput(
        @NotNull Integer qId,
        @NotNull SurveyOptionName optionName
) {
}
package com.nidus.twinly.me.dto.request;

import com.nidus.twinly.common.survey.SurveyAnswerInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MeSubmitTwinUpdateSurveyAnswerRequest(
        @NotNull UUID updateId,
        @NotNull @Valid SurveyAnswerInput answer
) {
}

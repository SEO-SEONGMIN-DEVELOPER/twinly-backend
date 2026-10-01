package com.nidus.twinly.me.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record MeSubmitTendencyAnswerRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long optionId
) {
}

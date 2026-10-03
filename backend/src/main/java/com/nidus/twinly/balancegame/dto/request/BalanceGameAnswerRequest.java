package com.nidus.twinly.balancegame.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record BalanceGameAnswerRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long optionId
) {
}

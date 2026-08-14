package com.nidus.twinly.me.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MeHesitationsAnswerRequest(
        @Schema(nullable = true)
        String answer,
        @NotNull Boolean skipped
) {
}

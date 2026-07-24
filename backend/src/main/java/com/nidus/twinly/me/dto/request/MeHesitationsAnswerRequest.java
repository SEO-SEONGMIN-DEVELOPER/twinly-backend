package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotNull;

public record MeHesitationsAnswerRequest(
        String answer,
        @NotNull Boolean skipped
) {
}

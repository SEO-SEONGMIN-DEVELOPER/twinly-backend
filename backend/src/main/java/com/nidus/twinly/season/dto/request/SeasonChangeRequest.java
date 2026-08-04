package com.nidus.twinly.season.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SeasonChangeRequest(
        @NotNull Instant startedAt,
        @NotNull Instant endedAt
) {
}

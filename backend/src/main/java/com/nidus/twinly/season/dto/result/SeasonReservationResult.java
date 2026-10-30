package com.nidus.twinly.season.dto.result;

import java.time.Instant;

public record SeasonReservationResult(
        Long seasonId,
        Instant startedAt,
        Instant endedAt
) {
}

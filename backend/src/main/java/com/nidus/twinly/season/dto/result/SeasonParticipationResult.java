package com.nidus.twinly.season.dto.result;

import java.time.Instant;

public record SeasonParticipationResult(
        Long currentSeasonId,
        Instant participatedInAt
) {
}

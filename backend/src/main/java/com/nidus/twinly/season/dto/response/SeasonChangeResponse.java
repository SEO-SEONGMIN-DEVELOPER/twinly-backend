package com.nidus.twinly.season.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.season.dto.result.SeasonChangeResult;

import java.time.Instant;

public record SeasonChangeResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId,
        Instant startedAt,
        Instant endedAt
) {

    public static SeasonChangeResponse from(SeasonChangeResult result) {
        return new SeasonChangeResponse(result.seasonId(), result.startedAt(), result.endedAt());
    }
}

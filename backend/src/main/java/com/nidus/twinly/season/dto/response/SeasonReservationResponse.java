package com.nidus.twinly.season.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.season.dto.result.SeasonReservationResult;

import java.time.Instant;

public record SeasonReservationResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId,
        Instant startedAt,
        Instant endedAt
) {

    public static SeasonReservationResponse from(SeasonReservationResult result) {
        return new SeasonReservationResponse(result.seasonId(), result.startedAt(), result.endedAt());
    }
}

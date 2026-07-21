package com.nidus.twinly.season.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;

import java.time.Instant;

public record SeasonParticipationResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long currentSeasonId,
        Instant participatedInAt
) {

    public static SeasonParticipationResponse from(SeasonParticipationResult result) {
        return new SeasonParticipationResponse(result.currentSeasonId(), result.participatedInAt());
    }
}

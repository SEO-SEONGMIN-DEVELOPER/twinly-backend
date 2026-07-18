package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.season.dto.result.SeasonParticipationResult;

import java.time.Instant;

public record SeasonParticipationResponse(
        Long currentSeasonId,
        Instant participatedInAt
) {

    public static SeasonParticipationResponse from(SeasonParticipationResult result) {
        return new SeasonParticipationResponse(result.currentSeasonId(), result.participatedInAt());
    }
}

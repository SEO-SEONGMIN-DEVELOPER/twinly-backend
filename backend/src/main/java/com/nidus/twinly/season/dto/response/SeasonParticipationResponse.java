package com.nidus.twinly.season.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record SeasonParticipationResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long currentSeasonId,
        @Schema(nullable = true)
        Instant participatedInAt
) {

    public static SeasonParticipationResponse from(SeasonParticipationResult result) {
        return new SeasonParticipationResponse(result.currentSeasonId(), result.participatedInAt());
    }
}

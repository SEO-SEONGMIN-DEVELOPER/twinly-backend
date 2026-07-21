package com.nidus.twinly.main.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.main.dto.result.MainTabSeasonResult;

import java.time.Instant;

public record MainTabSeasonResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId,
        Instant serverNow,
        String progress
) {

    public static MainTabSeasonResponse from(MainTabSeasonResult result) {
        return new MainTabSeasonResponse(result.seasonId(), result.serverNow(), result.progress());
    }
}

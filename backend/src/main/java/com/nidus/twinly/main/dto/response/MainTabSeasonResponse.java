package com.nidus.twinly.main.dto.response;

import com.nidus.twinly.main.dto.result.MainTabSeasonResult;

import java.time.Instant;

public record MainTabSeasonResponse(
        Long seasonId,
        Instant serverNow,
        String progress
) {

    public static MainTabSeasonResponse from(MainTabSeasonResult result) {
        return new MainTabSeasonResponse(result.seasonId(), result.serverNow(), result.progress());
    }
}

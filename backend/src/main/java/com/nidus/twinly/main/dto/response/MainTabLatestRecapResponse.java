package com.nidus.twinly.main.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.main.dto.result.MainTabLatestRecapResult;

import java.time.Instant;

public record MainTabLatestRecapResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId,
        Instant createdAt
) {

    public static MainTabLatestRecapResponse from(MainTabLatestRecapResult result) {
        return new MainTabLatestRecapResponse(result.seasonId(), result.createdAt());
    }
}

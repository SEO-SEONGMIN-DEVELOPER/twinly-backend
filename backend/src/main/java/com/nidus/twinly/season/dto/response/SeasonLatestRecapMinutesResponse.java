package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.season.dto.result.SeasonLatestRecapMinutesResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record SeasonLatestRecapMinutesResponse(
        Integer minutes,
        @Schema(nullable = true)
        Integer topPercent
) {

    public static SeasonLatestRecapMinutesResponse from(SeasonLatestRecapMinutesResult result) {
        return new SeasonLatestRecapMinutesResponse(result.minutes(), result.topPercent());
    }
}

package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.season.dto.result.SeasonLatestRecapCountResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record SeasonLatestRecapCountResponse(
        Integer count,
        @Schema(nullable = true)
        Integer topPercent
) {

    public static SeasonLatestRecapCountResponse from(SeasonLatestRecapCountResult result) {
        return new SeasonLatestRecapCountResponse(result.count(), result.topPercent());
    }
}

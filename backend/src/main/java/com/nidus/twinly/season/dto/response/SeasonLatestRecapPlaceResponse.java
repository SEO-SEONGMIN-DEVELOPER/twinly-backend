package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.season.dto.result.SeasonLatestRecapPlaceResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record SeasonLatestRecapPlaceResponse(
        @Schema(nullable = true)
        String placeCode,
        String name,
        Integer visitCount,
        Integer minutes
) {

    public static SeasonLatestRecapPlaceResponse from(SeasonLatestRecapPlaceResult result) {
        return new SeasonLatestRecapPlaceResponse(
                result.placeCode(),
                result.name(),
                result.visitCount(),
                result.minutes()
        );
    }
}

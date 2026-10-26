package com.nidus.twinly.season.dto.result;

public record SeasonLatestRecapPlaceResult(
        String placeCode,
        String name,
        Integer visitCount,
        Integer minutes
) {
}

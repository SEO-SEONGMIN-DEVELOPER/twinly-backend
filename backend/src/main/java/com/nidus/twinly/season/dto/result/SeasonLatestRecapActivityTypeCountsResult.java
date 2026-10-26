package com.nidus.twinly.season.dto.result;

public record SeasonLatestRecapActivityTypeCountsResult(
        Integer morning,
        Integer lunch,
        Integer evening,
        Integer lateNight
) {
}

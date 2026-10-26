package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityTypeCountsResult;

public record SeasonLatestRecapActivityTypeCountsResponse(
        Integer morning,
        Integer lunch,
        Integer evening,
        Integer lateNight
) {

    public static SeasonLatestRecapActivityTypeCountsResponse from(SeasonLatestRecapActivityTypeCountsResult result) {
        return new SeasonLatestRecapActivityTypeCountsResponse(
                result.morning(),
                result.lunch(),
                result.evening(),
                result.lateNight()
        );
    }
}

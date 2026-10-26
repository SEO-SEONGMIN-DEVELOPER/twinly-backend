package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.season.domain.SeasonRecapTimeSlot;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityTypeResult;

public record SeasonLatestRecapActivityTypeResponse(
        SeasonRecapTimeSlot type,
        SeasonLatestRecapActivityTypeCountsResponse counts
) {

    public static SeasonLatestRecapActivityTypeResponse from(SeasonLatestRecapActivityTypeResult result) {
        return new SeasonLatestRecapActivityTypeResponse(result.type(), SeasonLatestRecapActivityTypeCountsResponse.from(result.counts()));
    }
}

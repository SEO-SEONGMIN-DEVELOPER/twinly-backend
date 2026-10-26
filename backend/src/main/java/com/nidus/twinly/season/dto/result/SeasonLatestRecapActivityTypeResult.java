package com.nidus.twinly.season.dto.result;

import com.nidus.twinly.season.domain.SeasonRecapTimeSlot;

public record SeasonLatestRecapActivityTypeResult(
        SeasonRecapTimeSlot type,
        SeasonLatestRecapActivityTypeCountsResult counts
) {
}

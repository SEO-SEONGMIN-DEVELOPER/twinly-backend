package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.season.dto.result.SeasonLatestRecapTogetherResult;

public record SeasonLatestRecapTogetherResponse(
        Integer days
) {

    public static SeasonLatestRecapTogetherResponse from(SeasonLatestRecapTogetherResult result) {
        return new SeasonLatestRecapTogetherResponse(result.days());
    }
}

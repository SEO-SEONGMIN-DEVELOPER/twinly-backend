package com.nidus.twinly.season.dto.response;

import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityResult;

public record SeasonLatestRecapActivityResponse(
        SeasonLatestRecapMinutesResponse dialogue,
        SeasonLatestRecapMinutesResponse action,
        SeasonLatestRecapMinutesResponse move
) {

    public static SeasonLatestRecapActivityResponse from(SeasonLatestRecapActivityResult result) {
        return new SeasonLatestRecapActivityResponse(
                SeasonLatestRecapMinutesResponse.from(result.dialogue()),
                SeasonLatestRecapMinutesResponse.from(result.action()),
                SeasonLatestRecapMinutesResponse.from(result.move())
        );
    }
}

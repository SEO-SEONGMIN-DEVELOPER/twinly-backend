package com.nidus.twinly.season.dto.result;

public record SeasonLatestRecapActivityResult(
        SeasonLatestRecapMinutesResult dialogue,
        SeasonLatestRecapMinutesResult action,
        SeasonLatestRecapMinutesResult move
) {
}

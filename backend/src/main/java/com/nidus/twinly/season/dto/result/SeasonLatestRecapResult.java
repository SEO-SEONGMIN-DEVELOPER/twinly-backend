package com.nidus.twinly.season.dto.result;

import java.time.Instant;
import java.util.List;

public record SeasonLatestRecapResult(
        Long seasonId,
        Instant startedAt,
        Instant endedAt,
        Integer population,
        SeasonLatestRecapTogetherResult together,
        SeasonLatestRecapActivityResult activity,
        SeasonLatestRecapCountResult bubbles,
        List<SeasonLatestRecapPlaceResult> places,
        SeasonLatestRecapCountResult talked,
        SeasonLatestRecapCountResult friends,
        List<SeasonLatestRecapMostMetResult> mostMet,
        List<SeasonLatestRecapClosestResult> closest,
        List<SeasonLatestRecapBestMatchResult> bestMatches,
        SeasonLatestRecapCountResult profileViewers,
        SeasonLatestRecapActivityTypeResult activityType
) {
}

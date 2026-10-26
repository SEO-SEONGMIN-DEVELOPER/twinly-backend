package com.nidus.twinly.season.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record SeasonLatestRecapResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId,
        Instant startedAt,
        Instant endedAt,
        Integer population,
        SeasonLatestRecapTogetherResponse together,
        SeasonLatestRecapActivityResponse activity,
        SeasonLatestRecapCountResponse bubbles,
        List<SeasonLatestRecapPlaceResponse> places,
        SeasonLatestRecapCountResponse talked,
        SeasonLatestRecapCountResponse friends,
        List<SeasonLatestRecapMostMetResponse> mostMet,
        List<SeasonLatestRecapClosestResponse> closest,
        List<SeasonLatestRecapBestMatchResponse> bestMatches,
        SeasonLatestRecapCountResponse profileViewers,
        @Schema(nullable = true)
        SeasonLatestRecapActivityTypeResponse activityType
) {

    public static SeasonLatestRecapResponse from(SeasonLatestRecapResult result) {
        return new SeasonLatestRecapResponse(
                result.seasonId(),
                result.startedAt(),
                result.endedAt(),
                result.population(),
                SeasonLatestRecapTogetherResponse.from(result.together()),
                SeasonLatestRecapActivityResponse.from(result.activity()),
                SeasonLatestRecapCountResponse.from(result.bubbles()),
                result.places().stream().map(SeasonLatestRecapPlaceResponse::from).toList(),
                SeasonLatestRecapCountResponse.from(result.talked()),
                SeasonLatestRecapCountResponse.from(result.friends()),
                result.mostMet().stream().map(SeasonLatestRecapMostMetResponse::from).toList(),
                result.closest().stream().map(SeasonLatestRecapClosestResponse::from).toList(),
                result.bestMatches().stream().map(SeasonLatestRecapBestMatchResponse::from).toList(),
                SeasonLatestRecapCountResponse.from(result.profileViewers()),
                result.activityType() != null ? SeasonLatestRecapActivityTypeResponse.from(result.activityType()) : null
        );
    }
}

package com.nidus.twinly.season.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapBestMatchResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record SeasonLatestRecapBestMatchResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        @Schema(nullable = true)
        ProfilePhotoInfo profilePhoto,
        Double matchRate,
        Integer comparedCount,
        Integer matchedCount
) {

    public static SeasonLatestRecapBestMatchResponse from(SeasonLatestRecapBestMatchResult result) {
        return new SeasonLatestRecapBestMatchResponse(
                result.userId(),
                result.userName(),
                result.profilePhoto(),
                result.matchRate(),
                result.comparedCount(),
                result.matchedCount()
        );
    }
}

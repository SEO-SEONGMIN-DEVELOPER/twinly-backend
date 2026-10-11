package com.nidus.twinly.map.dto.response;

import com.nidus.twinly.map.dto.result.MapTwinSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record MapTwinSceneResponse(
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        @Schema(nullable = true)
        String placeCode,
        @Schema(nullable = true)
        String fromPlaceCode,
        @Schema(nullable = true)
        String travelMode
) {

    public static MapTwinSceneResponse from(MapTwinSceneResult result) {
        return new MapTwinSceneResponse(
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.placeCode(),
                result.fromPlaceCode(),
                result.travelMode()
        );
    }
}

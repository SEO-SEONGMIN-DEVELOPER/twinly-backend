package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventMoveSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventMoveSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String fromPlace,
        @Schema(nullable = true)
        String fromPlaceCode,
        String place,
        @Schema(nullable = true)
        String placeCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<Long> with,
        String travelMode,
        @Schema(nullable = true)
        String mapVersion,
        String narration,
        @Schema(nullable = true)
        String mind
) implements PeopleEventV2SceneResponse {

    public static PeopleEventMoveSceneResponse from(PeopleEventMoveSceneResult result) {
        return new PeopleEventMoveSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.fromPlace(),
                result.fromPlaceCode(),
                result.place(),
                result.placeCode(),
                result.with(),
                result.travelMode(),
                result.mapVersion(),
                result.narration(),
                result.mind()
        );
    }
}

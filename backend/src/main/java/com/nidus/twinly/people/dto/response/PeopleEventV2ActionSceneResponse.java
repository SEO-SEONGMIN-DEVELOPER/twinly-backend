package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventActionSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventV2ActionSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        @Schema(nullable = true)
        String placeCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        List<Long> with,
        String narration,
        @Schema(nullable = true)
        String mind,
        @Schema(nullable = true)
        String activity,
        @Schema(nullable = true)
        String motion,
        @Schema(nullable = true)
        List<PeopleEventV2LineResponse> lines
) implements PeopleEventV2SceneResponse {

    public static PeopleEventV2ActionSceneResponse from(PeopleEventActionSceneResult result) {
        return new PeopleEventV2ActionSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.placeCode(),
                result.with(),
                result.narration(),
                result.mind(),
                result.activity(),
                result.motion(),
                result.lines() == null ? null : result.lines().stream().map(PeopleEventV2LineResponse::from).toList()
        );
    }
}

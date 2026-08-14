package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventActionSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventActionSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        List<Long> with,
        String narration,
        @Schema(nullable = true)
        String mind
) implements PeopleEventSceneResponse {

    public static PeopleEventActionSceneResponse from(PeopleEventActionSceneResult result) {
        return new PeopleEventActionSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.with(),
                result.narration(),
                result.mind()
        );
    }
}

package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventDialogueSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventV2DialogueSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        @Schema(nullable = true)
        String placeCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<Long> with,
        List<PeopleEventV2LineResponse> lines
) implements PeopleEventV2SceneResponse {

    public static PeopleEventV2DialogueSceneResponse from(PeopleEventDialogueSceneResult result) {
        return new PeopleEventV2DialogueSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.placeCode(),
                result.with(),
                result.lines().stream().map(PeopleEventV2LineResponse::from).toList()
        );
    }
}

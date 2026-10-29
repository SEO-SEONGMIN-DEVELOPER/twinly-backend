package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.showcase.dto.result.ShowcaseDialogueSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record ShowcaseV2DialogueSceneResponse(
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
        List<ShowcaseV2LineResponse> lines
) implements ShowcaseV2SceneResponse {

    public static ShowcaseV2DialogueSceneResponse from(ShowcaseDialogueSceneResult result) {
        return new ShowcaseV2DialogueSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.placeCode(),
                result.with(),
                result.lines().stream().map(ShowcaseV2LineResponse::from).toList()
        );
    }
}

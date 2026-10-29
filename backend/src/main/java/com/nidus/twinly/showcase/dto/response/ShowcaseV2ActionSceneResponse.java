package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.showcase.dto.result.ShowcaseActionSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record ShowcaseV2ActionSceneResponse(
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
        String narration,
        @Schema(nullable = true)
        String mind,
        @Schema(nullable = true)
        List<ShowcaseV2LineResponse> lines
) implements ShowcaseV2SceneResponse {

    public static ShowcaseV2ActionSceneResponse from(ShowcaseActionSceneResult result) {
        return new ShowcaseV2ActionSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.placeCode(),
                result.with(),
                result.narration(),
                result.mind(),
                result.lines() == null ? null : result.lines().stream().map(ShowcaseV2LineResponse::from).toList()
        );
    }
}

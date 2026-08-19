package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.showcase.dto.result.ShowcaseActionSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record ShowcaseActionSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<Long> with,
        String narration,
        @Schema(nullable = true)
        String mind
) implements ShowcaseSceneResponse {

    public static ShowcaseActionSceneResponse from(ShowcaseActionSceneResult result) {
        return new ShowcaseActionSceneResponse(
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

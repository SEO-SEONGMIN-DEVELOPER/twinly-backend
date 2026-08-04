package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityActionSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityActionSceneResponse(
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
        String mind
) implements ActivitySceneResponse {

    public static ActivityActionSceneResponse from(ActivityActionSceneResult result) {
        return new ActivityActionSceneResponse(
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

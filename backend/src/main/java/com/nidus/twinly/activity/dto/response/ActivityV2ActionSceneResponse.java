package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityActionSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityV2ActionSceneResponse(
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
        List<ActivityV2LineResponse> lines
) implements ActivityV2SceneResponse {

    public static ActivityV2ActionSceneResponse from(ActivityActionSceneResult result) {
        return new ActivityV2ActionSceneResponse(
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
                result.lines() == null ? null : result.lines().stream().map(ActivityV2LineResponse::from).toList()
        );
    }
}

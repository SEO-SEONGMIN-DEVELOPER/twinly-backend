package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.common.scene.SceneNarrationLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record ActivityV2NarrationLineResponse(
        String t,
        String text,
        OffsetDateTime occursAt,
        @Schema(nullable = true)
        String speaker
) implements ActivityV2LineResponse {

    public static ActivityV2NarrationLineResponse from(SceneNarrationLine line) {
        return new ActivityV2NarrationLineResponse(line.t(), line.text(), line.occursAt(), line.speaker());
    }
}

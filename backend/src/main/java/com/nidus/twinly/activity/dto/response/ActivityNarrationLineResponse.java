package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.common.scene.SceneNarrationLine;

import java.time.OffsetDateTime;

public record ActivityNarrationLineResponse(
        String t,
        String text,
        OffsetDateTime occursAt
) implements ActivityLineResponse {

    public static ActivityNarrationLineResponse from(SceneNarrationLine line) {
        return new ActivityNarrationLineResponse(line.t(), line.text(), line.occursAt());
    }
}

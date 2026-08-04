package com.nidus.twinly.activity.dto.response;

import com.nidus.twinly.common.scene.SceneNarrationLine;

public record ActivityNarrationLineResponse(
        String t,
        String text
) implements ActivityLineResponse {

    public static ActivityNarrationLineResponse from(SceneNarrationLine line) {
        return new ActivityNarrationLineResponse(line.t(), line.text());
    }
}

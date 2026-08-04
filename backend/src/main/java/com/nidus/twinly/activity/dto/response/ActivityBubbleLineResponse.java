package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.scene.SceneBubbleLine;

public record ActivityBubbleLineResponse(
        String t,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String action,
        String text
) implements ActivityLineResponse {

    public static ActivityBubbleLineResponse from(SceneBubbleLine line) {
        return new ActivityBubbleLineResponse(
                line.t(),
                line.userId(),
                line.action(),
                line.text()
        );
    }
}

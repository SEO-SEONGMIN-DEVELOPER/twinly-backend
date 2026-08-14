package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.scene.SceneBubbleLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record PeopleEventBubbleLineResponse(
        String t,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @Schema(nullable = true)
        String action,
        String text,
        OffsetDateTime occursAt
) implements PeopleEventLineResponse {

    public static PeopleEventBubbleLineResponse from(SceneBubbleLine line) {
        return new PeopleEventBubbleLineResponse(
                line.t(),
                line.userId(),
                line.action(),
                line.text(),
                line.occursAt()
        );
    }
}

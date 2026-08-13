package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.scene.SceneBubbleLine;

import java.time.OffsetDateTime;

public record PeopleEventBubbleLineResponse(
        String t,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
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

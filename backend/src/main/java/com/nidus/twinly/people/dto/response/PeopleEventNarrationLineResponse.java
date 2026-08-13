package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.common.scene.SceneNarrationLine;

import java.time.OffsetDateTime;

public record PeopleEventNarrationLineResponse(
        String t,
        String text,
        OffsetDateTime occursAt
) implements PeopleEventLineResponse {

    public static PeopleEventNarrationLineResponse from(SceneNarrationLine line) {
        return new PeopleEventNarrationLineResponse(line.t(), line.text(), line.occursAt());
    }
}

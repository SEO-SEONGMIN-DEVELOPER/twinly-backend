package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.common.scene.SceneNarrationLine;

public record PeopleEventNarrationLineResponse(
        String t,
        String text
) implements PeopleEventLineResponse {

    public static PeopleEventNarrationLineResponse from(SceneNarrationLine line) {
        return new PeopleEventNarrationLineResponse(line.t(), line.text());
    }
}

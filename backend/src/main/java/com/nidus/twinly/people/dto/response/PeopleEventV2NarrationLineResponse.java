package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.common.scene.SceneNarrationLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record PeopleEventV2NarrationLineResponse(
        String t,
        String text,
        OffsetDateTime occursAt,
        @Schema(nullable = true)
        String speaker
) implements PeopleEventV2LineResponse {

    public static PeopleEventV2NarrationLineResponse from(SceneNarrationLine line) {
        return new PeopleEventV2NarrationLineResponse(line.t(), line.text(), line.occursAt(), line.speaker());
    }
}

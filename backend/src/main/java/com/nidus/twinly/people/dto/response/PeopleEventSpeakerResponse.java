package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventSpeakerResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PeopleEventSpeakerResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @Schema(nullable = true)
        String userName
) {

    public static PeopleEventSpeakerResponse from(PeopleEventSpeakerResult result) {
        return new PeopleEventSpeakerResponse(result.userId(), result.userName());
    }
}

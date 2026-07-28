package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventDialogueSceneResult;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventDialogueSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<PeopleEventSpeakerResponse> with,
        List<PeopleEventLineResponse> lines
) implements PeopleEventSceneResponse {

    public static PeopleEventDialogueSceneResponse from(PeopleEventDialogueSceneResult result) {
        return new PeopleEventDialogueSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.with().stream().map(PeopleEventSpeakerResponse::from).toList(),
                result.lines().stream().map(PeopleEventLineResponse::from).toList()
        );
    }
}

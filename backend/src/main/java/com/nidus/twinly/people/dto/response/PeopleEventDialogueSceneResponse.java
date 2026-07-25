package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.activity.dto.response.ActivityLineResponse;
import com.nidus.twinly.activity.dto.response.ActivitySpeakerResponse;
import com.nidus.twinly.people.dto.result.PeopleEventDialogueSceneResult;

import java.time.Instant;
import java.util.List;

public record PeopleEventDialogueSceneResponse(
        String type,
        Instant startsAt,
        Instant endsAt,
        String place,
        List<ActivitySpeakerResponse> with,
        List<ActivityLineResponse> lines
) implements PeopleEventSceneResponse {

    public static PeopleEventDialogueSceneResponse from(PeopleEventDialogueSceneResult result) {
        return new PeopleEventDialogueSceneResponse(
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.with().stream().map(ActivitySpeakerResponse::from).toList(),
                result.lines().stream().map(ActivityLineResponse::from).toList()
        );
    }
}

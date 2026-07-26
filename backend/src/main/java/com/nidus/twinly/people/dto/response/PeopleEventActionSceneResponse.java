package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.activity.dto.response.ActivitySpeakerResponse;
import com.nidus.twinly.people.dto.result.PeopleEventActionSceneResult;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventActionSceneResponse(
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<ActivitySpeakerResponse> with,
        String narration,
        String mind
) implements PeopleEventSceneResponse {

    public static PeopleEventActionSceneResponse from(PeopleEventActionSceneResult result) {
        return new PeopleEventActionSceneResponse(
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.with().stream().map(ActivitySpeakerResponse::from).toList(),
                result.narration(),
                result.mind()
        );
    }
}

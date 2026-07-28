package com.nidus.twinly.people.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventDialogueSceneResult(
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<PeopleEventSpeakerResult> with,
        List<PeopleEventLineResult> lines
) implements PeopleEventSceneResult {
}

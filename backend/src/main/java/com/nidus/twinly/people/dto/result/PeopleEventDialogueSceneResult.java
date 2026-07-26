package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.activity.dto.result.ActivityLineResult;
import com.nidus.twinly.activity.dto.result.ActivitySpeakerResult;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventDialogueSceneResult(
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<ActivitySpeakerResult> with,
        List<ActivityLineResult> lines
) implements PeopleEventSceneResult {
}

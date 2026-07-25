package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.activity.dto.result.ActivitySpeakerResult;

import java.time.Instant;
import java.util.List;

public record PeopleEventActionSceneResult(
        String type,
        Instant startsAt,
        Instant endsAt,
        String place,
        List<ActivitySpeakerResult> with,
        String narration,
        String mind
) implements PeopleEventSceneResult {
}

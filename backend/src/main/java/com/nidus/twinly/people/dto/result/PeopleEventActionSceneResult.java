package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.activity.dto.result.ActivitySpeakerResult;

import java.time.LocalDateTime;
import java.util.List;

public record PeopleEventActionSceneResult(
        String type,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String place,
        List<ActivitySpeakerResult> with,
        String narration,
        String mind
) implements PeopleEventSceneResult {
}

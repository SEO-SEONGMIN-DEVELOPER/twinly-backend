package com.nidus.twinly.activity.dto.result;

import java.time.Instant;
import java.util.List;

public record ActivityActionSceneResult(
        String type,
        Instant startsAt,
        Instant endsAt,
        String place,
        List<ActivitySpeakerResult> with,
        String narration,
        String mind
) implements ActivitySceneResult {
}

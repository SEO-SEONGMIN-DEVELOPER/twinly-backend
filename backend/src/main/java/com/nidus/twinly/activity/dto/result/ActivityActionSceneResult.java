package com.nidus.twinly.activity.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityActionSceneResult(
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<ActivitySpeakerResult> with,
        String narration,
        String mind
) implements ActivitySceneResult {
}

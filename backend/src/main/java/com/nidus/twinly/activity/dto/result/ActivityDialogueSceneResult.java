package com.nidus.twinly.activity.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityDialogueSceneResult(
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<ActivitySpeakerResult> with,
        List<ActivityLineResult> lines
) implements ActivitySceneResult {
}

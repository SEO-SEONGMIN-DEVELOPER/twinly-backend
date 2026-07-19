package com.nidus.twinly.activity.dto.result;

import java.time.Instant;
import java.util.List;

public record ActivityDialogueSceneResult(
        String type,
        Instant startsAt,
        Instant endsAt,
        String place,
        List<ActivitySpeakerResult> with,
        List<ActivityLineResult> lines
) implements ActivitySceneResult {
}

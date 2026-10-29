package com.nidus.twinly.activity.dto.result;

import com.nidus.twinly.common.scene.SceneLine;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityActionSceneResult(
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        String placeCode,
        List<Long> with,
        String narration,
        String mind,
        String activity,
        String motion,
        List<SceneLine> lines
) implements ActivitySceneResult {
}

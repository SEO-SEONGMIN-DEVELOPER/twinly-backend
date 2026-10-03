package com.nidus.twinly.showcase.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record ShowcaseActionSceneResult(
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        String placeCode,
        List<Long> with,
        String narration,
        String mind
) implements ShowcaseSceneResult {
}

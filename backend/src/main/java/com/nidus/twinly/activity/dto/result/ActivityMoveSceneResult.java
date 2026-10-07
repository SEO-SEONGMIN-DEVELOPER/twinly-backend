package com.nidus.twinly.activity.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityMoveSceneResult(
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String fromPlace,
        String fromPlaceCode,
        String place,
        String placeCode,
        List<Long> with,
        String travelMode,
        String mapVersion,
        String narration,
        String mind
) implements ActivitySceneResult {
}

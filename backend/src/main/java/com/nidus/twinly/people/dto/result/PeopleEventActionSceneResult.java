package com.nidus.twinly.people.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventActionSceneResult(
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<Long> with,
        String narration,
        String mind
) implements PeopleEventSceneResult {
}

package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.scene.SceneLine;

import java.time.OffsetDateTime;
import java.util.List;

public record PeopleEventDialogueSceneResult(
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        List<Long> with,
        List<SceneLine> lines
) implements PeopleEventSceneResult {
}

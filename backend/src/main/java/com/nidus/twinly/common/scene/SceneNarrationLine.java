package com.nidus.twinly.common.scene;

import com.nidus.twinly.common.time.KstTimes;

import java.time.OffsetDateTime;

public record SceneNarrationLine(
        String t,
        String text,
        OffsetDateTime occursAt
) implements SceneLine {

    public static SceneNarrationLine from(StoredSceneNarrationLine line) {
        return new SceneNarrationLine(
                line.t(),
                line.text(),
                KstTimes.toKstOffsetDateTime(line.occursAt())
        );
    }
}

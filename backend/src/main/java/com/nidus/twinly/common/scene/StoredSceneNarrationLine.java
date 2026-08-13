package com.nidus.twinly.common.scene;

import java.time.LocalTime;

public record StoredSceneNarrationLine(
        String t,
        String text,
        LocalTime occursAt
) implements StoredSceneLine {
}

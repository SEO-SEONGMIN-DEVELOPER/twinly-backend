package com.nidus.twinly.common.scene;

import java.time.LocalDateTime;

public record StoredSceneNarrationLine(
        String t,
        String text,
        LocalDateTime occursAt
) implements StoredSceneLine {
}

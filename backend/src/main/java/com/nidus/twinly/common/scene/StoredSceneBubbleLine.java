package com.nidus.twinly.common.scene;

import java.time.LocalTime;

public record StoredSceneBubbleLine(
        String t,
        Long userId,
        String action,
        String text,
        LocalTime occursAt
) implements StoredSceneLine {
}

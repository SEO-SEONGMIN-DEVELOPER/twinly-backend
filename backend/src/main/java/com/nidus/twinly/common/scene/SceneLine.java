package com.nidus.twinly.common.scene;

import java.time.LocalDate;

public sealed interface SceneLine permits SceneNarrationLine, SceneBubbleLine {

    static SceneLine from(StoredSceneLine line, LocalDate date) {
        return switch (line) {
            case StoredSceneNarrationLine l -> SceneNarrationLine.from(l, date);
            case StoredSceneBubbleLine l -> SceneBubbleLine.from(l, date);
        };
    }
}

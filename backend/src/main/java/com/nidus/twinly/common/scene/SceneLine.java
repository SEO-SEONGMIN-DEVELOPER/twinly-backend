package com.nidus.twinly.common.scene;

public sealed interface SceneLine permits SceneNarrationLine, SceneBubbleLine {

    static SceneLine from(StoredSceneLine line) {
        return switch (line) {
            case StoredSceneNarrationLine l -> SceneNarrationLine.from(l);
            case StoredSceneBubbleLine l -> SceneBubbleLine.from(l);
        };
    }
}

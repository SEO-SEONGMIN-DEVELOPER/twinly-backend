package com.nidus.twinly.common.scene;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StoredSceneNarrationLine.class, name = "narr"),
        @JsonSubTypes.Type(value = StoredSceneBubbleLine.class, name = "bubble")
})
public sealed interface StoredSceneLine permits StoredSceneNarrationLine, StoredSceneBubbleLine {
}

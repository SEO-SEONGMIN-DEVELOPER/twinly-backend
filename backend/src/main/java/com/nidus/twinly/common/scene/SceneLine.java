package com.nidus.twinly.common.scene;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SceneNarrationLine.class, name = "narr"),
        @JsonSubTypes.Type(value = SceneBubbleLine.class, name = "bubble")
})
public sealed interface SceneLine permits SceneNarrationLine, SceneBubbleLine {
}

package com.nidus.twinly.activity.dto.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityActionSceneResult.class, name = "action"),
        @JsonSubTypes.Type(value = ActivityDialogueSceneResult.class, name = "dialogue"),
        @JsonSubTypes.Type(value = ActivityMoveSceneResult.class, name = "move")
})
public sealed interface ActivitySceneResult permits ActivityActionSceneResult, ActivityDialogueSceneResult, ActivityMoveSceneResult {
}

package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.activity.dto.result.ActivityActionSceneResult;
import com.nidus.twinly.activity.dto.result.ActivityDialogueSceneResult;
import com.nidus.twinly.activity.dto.result.ActivityMoveSceneResult;
import com.nidus.twinly.activity.dto.result.ActivitySceneResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityV2ActionSceneResponse.class, name = "action"),
        @JsonSubTypes.Type(value = ActivityV2DialogueSceneResponse.class, name = "dialogue"),
        @JsonSubTypes.Type(value = ActivityMoveSceneResponse.class, name = "move")
})
public sealed interface ActivityV2SceneResponse permits ActivityV2ActionSceneResponse, ActivityV2DialogueSceneResponse, ActivityMoveSceneResponse {

    static ActivityV2SceneResponse from(ActivitySceneResult result) {
        return switch (result) {
            case ActivityActionSceneResult r -> ActivityV2ActionSceneResponse.from(r);
            case ActivityDialogueSceneResult r -> ActivityV2DialogueSceneResponse.from(r);
            case ActivityMoveSceneResult r -> ActivityMoveSceneResponse.from(r);
        };
    }
}

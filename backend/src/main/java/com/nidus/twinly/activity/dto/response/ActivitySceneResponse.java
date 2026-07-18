package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.activity.dto.result.ActivityActionSceneResult;
import com.nidus.twinly.activity.dto.result.ActivityDialogueSceneResult;
import com.nidus.twinly.activity.dto.result.ActivitySceneResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityActionSceneResponse.class, name = "action"),
        @JsonSubTypes.Type(value = ActivityDialogueSceneResponse.class, name = "dialogue")
})
public sealed interface ActivitySceneResponse permits ActivityActionSceneResponse, ActivityDialogueSceneResponse {

    static ActivitySceneResponse from(ActivitySceneResult result) {
        return switch (result) {
            case ActivityActionSceneResult r -> ActivityActionSceneResponse.from(r);
            case ActivityDialogueSceneResult r -> ActivityDialogueSceneResponse.from(r);
        };
    }
}

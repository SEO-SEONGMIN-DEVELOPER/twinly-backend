package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.showcase.dto.result.ShowcaseActionSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseDialogueSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseMoveSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseSceneResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ShowcaseV2ActionSceneResponse.class, name = "action"),
        @JsonSubTypes.Type(value = ShowcaseV2DialogueSceneResponse.class, name = "dialogue"),
        @JsonSubTypes.Type(value = ShowcaseMoveSceneResponse.class, name = "move")
})
public sealed interface ShowcaseV2SceneResponse permits ShowcaseV2ActionSceneResponse, ShowcaseV2DialogueSceneResponse, ShowcaseMoveSceneResponse {

    static ShowcaseV2SceneResponse from(ShowcaseSceneResult result) {
        return switch (result) {
            case ShowcaseActionSceneResult r -> ShowcaseV2ActionSceneResponse.from(r);
            case ShowcaseDialogueSceneResult r -> ShowcaseV2DialogueSceneResponse.from(r);
            case ShowcaseMoveSceneResult r -> ShowcaseMoveSceneResponse.from(r);
        };
    }
}

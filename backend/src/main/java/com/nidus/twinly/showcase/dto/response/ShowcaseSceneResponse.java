package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.showcase.dto.result.ShowcaseActionSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseDialogueSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseSceneResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ShowcaseActionSceneResponse.class, name = "action"),
        @JsonSubTypes.Type(value = ShowcaseDialogueSceneResponse.class, name = "dialogue")
})
public sealed interface ShowcaseSceneResponse permits ShowcaseActionSceneResponse, ShowcaseDialogueSceneResponse {

    static ShowcaseSceneResponse from(ShowcaseSceneResult result) {
        return switch (result) {
            case ShowcaseActionSceneResult scene -> ShowcaseActionSceneResponse.from(scene);
            case ShowcaseDialogueSceneResult scene -> ShowcaseDialogueSceneResponse.from(scene);
        };
    }
}

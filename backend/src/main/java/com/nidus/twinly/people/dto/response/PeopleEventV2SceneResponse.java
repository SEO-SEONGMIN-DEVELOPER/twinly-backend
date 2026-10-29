package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.people.dto.result.PeopleEventActionSceneResult;
import com.nidus.twinly.people.dto.result.PeopleEventDialogueSceneResult;
import com.nidus.twinly.people.dto.result.PeopleEventMoveSceneResult;
import com.nidus.twinly.people.dto.result.PeopleEventSceneResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PeopleEventV2ActionSceneResponse.class, name = "action"),
        @JsonSubTypes.Type(value = PeopleEventV2DialogueSceneResponse.class, name = "dialogue"),
        @JsonSubTypes.Type(value = PeopleEventMoveSceneResponse.class, name = "move")
})
public sealed interface PeopleEventV2SceneResponse permits PeopleEventV2ActionSceneResponse, PeopleEventV2DialogueSceneResponse, PeopleEventMoveSceneResponse {

    static PeopleEventV2SceneResponse from(PeopleEventSceneResult result) {
        return switch (result) {
            case PeopleEventActionSceneResult r -> PeopleEventV2ActionSceneResponse.from(r);
            case PeopleEventDialogueSceneResult r -> PeopleEventV2DialogueSceneResponse.from(r);
            case PeopleEventMoveSceneResult r -> PeopleEventMoveSceneResponse.from(r);
        };
    }
}

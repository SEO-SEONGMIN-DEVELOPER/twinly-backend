package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.people.dto.result.PeopleEventActionSceneResult;
import com.nidus.twinly.people.dto.result.PeopleEventDialogueSceneResult;
import com.nidus.twinly.people.dto.result.PeopleEventSceneResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PeopleEventActionSceneResponse.class, name = "action"),
        @JsonSubTypes.Type(value = PeopleEventDialogueSceneResponse.class, name = "dialogue")
})
public sealed interface PeopleEventSceneResponse permits PeopleEventActionSceneResponse, PeopleEventDialogueSceneResponse {

    static PeopleEventSceneResponse from(PeopleEventSceneResult result) {
        return switch (result) {
            case PeopleEventActionSceneResult r -> PeopleEventActionSceneResponse.from(r);
            case PeopleEventDialogueSceneResult r -> PeopleEventDialogueSceneResponse.from(r);
        };
    }
}

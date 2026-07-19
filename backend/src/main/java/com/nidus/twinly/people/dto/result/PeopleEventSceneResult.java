package com.nidus.twinly.people.dto.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PeopleEventActionSceneResult.class, name = "action"),
        @JsonSubTypes.Type(value = PeopleEventDialogueSceneResult.class, name = "dialogue")
})
public sealed interface PeopleEventSceneResult permits PeopleEventActionSceneResult, PeopleEventDialogueSceneResult {
}

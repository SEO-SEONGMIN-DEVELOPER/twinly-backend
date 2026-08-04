package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimulationsActionSceneRequest.class, name = "action"),
        @JsonSubTypes.Type(value = SimulationsDialogueSceneRequest.class, name = "dialogue")
})
public sealed interface SimulationsSceneRequest permits SimulationsActionSceneRequest, SimulationsDialogueSceneRequest {
}

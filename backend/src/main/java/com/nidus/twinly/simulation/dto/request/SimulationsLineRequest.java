package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimulationsNarrationLineRequest.class, name = "narr"),
        @JsonSubTypes.Type(value = SimulationsBubbleLineRequest.class, name = "bubble")
})
public sealed interface SimulationsLineRequest permits SimulationsNarrationLineRequest, SimulationsBubbleLineRequest {
}

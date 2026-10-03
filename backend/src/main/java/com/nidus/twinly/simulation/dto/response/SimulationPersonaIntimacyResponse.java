package com.nidus.twinly.simulation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaIntimacyResult;

public record SimulationPersonaIntimacyResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long partnerId,
        Integer intimacy
) {

    public static SimulationPersonaIntimacyResponse from(SimulationPersonaIntimacyResult result) {
        return new SimulationPersonaIntimacyResponse(result.partnerId(), result.intimacy());
    }
}

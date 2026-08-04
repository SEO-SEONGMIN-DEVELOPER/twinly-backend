package com.nidus.twinly.simulation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SimulationPersonaResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String userName,
        Gender gender,
        String affiliation,
        LocalDate birthDate,
        Map<PersonaDimension, List<String>> personaElements
) {

    public static SimulationPersonaResponse from(SimulationPersonaResult result) {
        return new SimulationPersonaResponse(result.userId(), result.userName(), result.gender(), result.affiliation(), result.birthDate(), result.personaElements());
    }
}

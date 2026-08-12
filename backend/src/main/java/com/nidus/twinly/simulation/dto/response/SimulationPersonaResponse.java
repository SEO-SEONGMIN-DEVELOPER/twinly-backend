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
        String familyName,
        String givenName,
        Gender gender,
        String school,
        String affiliation,
        LocalDate birthDate,
        Map<PersonaDimension, List<String>> personaElements
) {

    public static SimulationPersonaResponse from(SimulationPersonaResult result) {
        return new SimulationPersonaResponse(result.userId(), result.familyName(), result.givenName(), result.gender(), result.school(), result.affiliation(), result.birthDate(), result.personaElements());
    }
}

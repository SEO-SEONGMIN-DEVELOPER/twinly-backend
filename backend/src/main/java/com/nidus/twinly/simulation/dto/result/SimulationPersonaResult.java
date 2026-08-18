package com.nidus.twinly.simulation.dto.result;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SimulationPersonaResult(
        Long userId,
        String familyName,
        String givenName,
        Gender gender,
        String organization,
        String affiliation,
        LocalDate birthDate,
        Map<PersonaDimension, List<String>> personaElements
) {
}

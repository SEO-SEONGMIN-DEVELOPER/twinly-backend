package com.nidus.twinly.simulation.dto.result;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SimulationPersonaResult(
        Long userId,
        String userName,
        Gender gender,
        String affiliation,
        LocalDate birthDate,
        Map<PersonaDimension, List<String>> personaElements
) {
}

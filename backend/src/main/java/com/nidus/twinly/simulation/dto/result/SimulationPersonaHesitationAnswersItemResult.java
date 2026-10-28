package com.nidus.twinly.simulation.dto.result;

import java.time.LocalDate;
import java.util.List;

public record SimulationPersonaHesitationAnswersItemResult(
        LocalDate date,
        String text,
        List<String> options,
        String choice
) {
}

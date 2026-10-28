package com.nidus.twinly.simulation.dto.response;

import com.nidus.twinly.simulation.dto.result.SimulationPersonaHesitationAnswersItemResult;

import java.time.LocalDate;
import java.util.List;

public record SimulationPersonaHesitationAnswersItemResponse(
        LocalDate date,
        String text,
        List<String> options,
        String choice
) {

    public static SimulationPersonaHesitationAnswersItemResponse from(SimulationPersonaHesitationAnswersItemResult result) {
        return new SimulationPersonaHesitationAnswersItemResponse(
                result.date(),
                result.text(),
                result.options(),
                result.choice()
        );
    }
}

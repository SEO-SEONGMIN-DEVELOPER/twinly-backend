package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsNarrationLineRequest;

public record SimulationsNarrationLineCommand(
        String t,
        String text
) implements SimulationsLineCommand {

    public static SimulationsNarrationLineCommand from(SimulationsNarrationLineRequest request) {
        return new SimulationsNarrationLineCommand(request.t(), request.text());
    }
}

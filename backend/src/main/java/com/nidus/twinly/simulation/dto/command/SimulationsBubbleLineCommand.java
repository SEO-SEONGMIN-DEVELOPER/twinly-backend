package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsBubbleLineRequest;

public record SimulationsBubbleLineCommand(
        String t,
        Long userId,
        String action,
        String text
) implements SimulationsLineCommand {

    public static SimulationsBubbleLineCommand from(SimulationsBubbleLineRequest request) {
        return new SimulationsBubbleLineCommand(request.t(), request.userId(), request.action(), request.text());
    }
}

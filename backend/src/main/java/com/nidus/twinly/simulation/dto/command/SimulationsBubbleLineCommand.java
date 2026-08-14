package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsBubbleLineRequest;

import java.time.LocalDateTime;

public record SimulationsBubbleLineCommand(
        String t,
        Long userId,
        String action,
        String text,
        LocalDateTime occursAt
) implements SimulationsLineCommand {

    public static SimulationsBubbleLineCommand from(SimulationsBubbleLineRequest request) {
        return new SimulationsBubbleLineCommand(request.t(), request.userId(), request.action(), request.text(),
                request.occursAt());
    }
}

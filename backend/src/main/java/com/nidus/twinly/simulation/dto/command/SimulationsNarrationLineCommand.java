package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsNarrationLineRequest;

import java.time.LocalDateTime;

public record SimulationsNarrationLineCommand(
        String t,
        String text,
        LocalDateTime occursAt,
        String speaker
) implements SimulationsLineCommand {

    public static SimulationsNarrationLineCommand from(SimulationsNarrationLineRequest request) {
        return new SimulationsNarrationLineCommand(request.t(), request.text(), request.occursAt(), request.speaker());
    }
}

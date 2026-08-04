package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsBubbleLineRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsLineRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsNarrationLineRequest;

public sealed interface SimulationsLineCommand permits SimulationsNarrationLineCommand, SimulationsBubbleLineCommand {

    static SimulationsLineCommand from(SimulationsLineRequest request) {
        return switch (request) {
            case SimulationsNarrationLineRequest r -> SimulationsNarrationLineCommand.from(r);
            case SimulationsBubbleLineRequest r -> SimulationsBubbleLineCommand.from(r);
        };
    }
}

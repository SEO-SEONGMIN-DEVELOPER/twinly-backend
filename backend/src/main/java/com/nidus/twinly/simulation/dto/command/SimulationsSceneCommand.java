package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsActionSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsDialogueSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsMoveSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsSceneRequest;

public sealed interface SimulationsSceneCommand permits SimulationsActionSceneCommand, SimulationsDialogueSceneCommand, SimulationsMoveSceneCommand {

    static SimulationsSceneCommand from(SimulationsSceneRequest request) {
        return switch (request) {
            case SimulationsActionSceneRequest r -> SimulationsActionSceneCommand.from(r);
            case SimulationsDialogueSceneRequest r -> SimulationsDialogueSceneCommand.from(r);
            case SimulationsMoveSceneRequest r -> SimulationsMoveSceneCommand.from(r);
        };
    }
}

package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsActionSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsDialogueSceneRequest;
import com.nidus.twinly.simulation.dto.request.SimulationsSceneRequest;

public sealed interface SimulationsSceneCommand permits SimulationsActionSceneCommand, SimulationsDialogueSceneCommand {

    static SimulationsSceneCommand from(SimulationsSceneRequest request) {
        return switch (request) {
            case SimulationsActionSceneRequest r -> SimulationsActionSceneCommand.from(r);
            case SimulationsDialogueSceneRequest r -> SimulationsDialogueSceneCommand.from(r);
        };
    }
}

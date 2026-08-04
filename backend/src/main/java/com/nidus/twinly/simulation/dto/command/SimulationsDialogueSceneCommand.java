package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsDialogueSceneRequest;

import java.time.LocalTime;
import java.util.List;

public record SimulationsDialogueSceneCommand(
        LocalTime start,
        LocalTime end,
        String type,
        String place,
        List<Long> with,
        List<SimulationsLineCommand> lines
) implements SimulationsSceneCommand {

    public static SimulationsDialogueSceneCommand from(SimulationsDialogueSceneRequest request) {
        return new SimulationsDialogueSceneCommand(
                request.start(),
                request.end(),
                request.type(),
                request.place(),
                request.with(),
                request.lines().stream().map(SimulationsLineCommand::from).toList()
        );
    }
}

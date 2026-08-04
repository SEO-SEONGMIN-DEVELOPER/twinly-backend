package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsActionSceneRequest;

import java.time.LocalTime;
import java.util.List;

public record SimulationsActionSceneCommand(
        LocalTime start,
        LocalTime end,
        String type,
        String place,
        List<Long> with,
        String narration,
        String mind
) implements SimulationsSceneCommand {

    public static SimulationsActionSceneCommand from(SimulationsActionSceneRequest request) {
        return new SimulationsActionSceneCommand(
                request.start(),
                request.end(),
                request.type(),
                request.place(),
                request.with(),
                request.narration(),
                request.mind()
        );
    }
}

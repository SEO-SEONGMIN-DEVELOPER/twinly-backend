package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsActionSceneRequest;

import java.time.LocalDateTime;
import java.util.List;

public record SimulationsActionSceneCommand(
        LocalDateTime start,
        LocalDateTime end,
        String type,
        String place,
        String placeCode,
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
                request.placeCode(),
                request.with(),
                request.narration(),
                request.mind()
        );
    }
}

package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsMoveSceneRequest;

import java.time.LocalDateTime;
import java.util.List;

public record SimulationsMoveSceneCommand(
        LocalDateTime start,
        LocalDateTime end,
        String type,
        String fromPlace,
        String fromPlaceCode,
        String place,
        String placeCode,
        List<Long> with,
        String travelMode,
        String mapVersion,
        String narration,
        String mind
) implements SimulationsSceneCommand {

    public static SimulationsMoveSceneCommand from(SimulationsMoveSceneRequest request) {
        return new SimulationsMoveSceneCommand(
                request.start(),
                request.end(),
                request.type(),
                request.fromPlace(),
                request.fromPlaceCode(),
                request.place(),
                request.placeCode(),
                request.with(),
                request.travelMode(),
                request.mapVersion(),
                request.narration(),
                request.mind()
        );
    }
}

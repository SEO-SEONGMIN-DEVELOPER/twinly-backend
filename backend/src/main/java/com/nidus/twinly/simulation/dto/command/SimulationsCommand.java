package com.nidus.twinly.simulation.dto.command;

import com.nidus.twinly.simulation.dto.request.SimulationsRequest;

import java.time.LocalDate;
import java.util.List;

public record SimulationsCommand(
        Long userId,
        LocalDate date,
        List<SimulationsSceneCommand> scenes,
        List<SimulationsQuestionCommand> questions,
        List<SimulationsRelationshipCommand> relationships
) {

    public static SimulationsCommand from(SimulationsRequest request) {
        return new SimulationsCommand(
                request.userId(),
                request.date(),
                request.scenes().stream().map(SimulationsSceneCommand::from).toList(),
                request.questions().stream().map(SimulationsQuestionCommand::from).toList(),
                request.relationships().stream().map(SimulationsRelationshipCommand::from).toList()
        );
    }
}

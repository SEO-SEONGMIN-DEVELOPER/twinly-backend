package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SimulationsRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long userId,
        @NotNull LocalDate date,
        @Valid @NotNull List<SimulationsSceneRequest> scenes,
        @Valid @NotNull List<SimulationsQuestionRequest> questions,
        @Valid @NotNull List<SimulationsRelationshipRequest> relationships
) {
}

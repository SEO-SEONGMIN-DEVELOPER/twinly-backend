package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SimulationsRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long userId,
        @NotNull LocalDate date,
        @Valid @NotNull List<SimulationsSceneRequest> scenes,
        @Valid @NotNull List<SimulationsQuestionRequest> questions,
        @Valid @NotNull List<SimulationsRelationshipRequest> relationships,
        @NotNull LocalDateTime intimacyAsOf,
        @Schema(nullable = true)
        ObjectNode room,
        @Schema(nullable = true)
        ArrayNode homeObjects
) {
}

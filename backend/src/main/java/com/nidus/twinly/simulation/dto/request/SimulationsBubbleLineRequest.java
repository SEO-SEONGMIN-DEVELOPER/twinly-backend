package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SimulationsBubbleLineRequest(
        @NotBlank String t,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long userId,
        @NotBlank String action,
        @NotBlank String text
) implements SimulationsLineRequest {
}

package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SimulationsBubbleLineRequest(
        @NotBlank String t,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long userId,
        @NotBlank String action,
        @NotBlank String text,
        @NotNull LocalTime occursAt
) implements SimulationsLineRequest {
}

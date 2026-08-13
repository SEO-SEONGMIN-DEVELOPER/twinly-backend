package com.nidus.twinly.simulation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SimulationsNarrationLineRequest(
        @NotBlank String t,
        @NotBlank String text,
        @NotNull LocalTime occursAt
) implements SimulationsLineRequest {
}

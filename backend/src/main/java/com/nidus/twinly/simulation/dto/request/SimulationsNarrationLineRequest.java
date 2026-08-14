package com.nidus.twinly.simulation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SimulationsNarrationLineRequest(
        @NotBlank String t,
        @NotBlank String text,
        @NotNull LocalDateTime occursAt
) implements SimulationsLineRequest {
}

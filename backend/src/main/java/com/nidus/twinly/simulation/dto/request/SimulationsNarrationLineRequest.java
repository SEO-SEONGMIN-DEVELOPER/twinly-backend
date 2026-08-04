package com.nidus.twinly.simulation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SimulationsNarrationLineRequest(
        @NotBlank String t,
        @NotBlank String text
) implements SimulationsLineRequest {
}

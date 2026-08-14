package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SimulationsBubbleLineRequest(
        @NotBlank String t,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long userId,
        @Schema(nullable = true)
        String action,
        @NotBlank String text,
        @NotNull LocalDateTime occursAt
) implements SimulationsLineRequest {
}

package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record SimulationsActionSceneRequest(
        @NotNull LocalDateTime start,
        @NotNull LocalDateTime end,
        @NotBlank String type,
        @NotBlank String place,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        List<Long> with,
        @NotBlank String narration,
        @Schema(nullable = true)
        String mind
) implements SimulationsSceneRequest {
}

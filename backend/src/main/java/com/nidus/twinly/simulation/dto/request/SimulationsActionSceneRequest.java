package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record SimulationsActionSceneRequest(
        @NotNull LocalDateTime start,
        @NotNull LocalDateTime end,
        @NotBlank String type,
        @NotBlank String place,
        @Schema(nullable = true)
        String placeCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        List<Long> with,
        @NotBlank String narration,
        @Schema(nullable = true)
        String mind,
        @Size(max = 100)
        @Schema(nullable = true)
        String activity,
        @Size(max = 100)
        @Schema(nullable = true)
        String motion,
        @Schema(nullable = true)
        List<@Valid SimulationsNarrationLineRequest> lines
) implements SimulationsSceneRequest {
}

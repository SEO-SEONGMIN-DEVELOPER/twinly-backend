package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record SimulationsMoveSceneRequest(
        @NotNull LocalDateTime start,
        @NotNull LocalDateTime end,
        @NotBlank String type,
        @JsonProperty("from")
        @NotBlank String fromPlace,
        @Schema(nullable = true)
        String fromPlaceCode,
        @NotBlank String place,
        @Schema(nullable = true)
        String placeCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        List<Long> with,
        @NotBlank String travelMode,
        @Schema(nullable = true)
        String mapVersion,
        @NotBlank String narration,
        @Schema(nullable = true)
        String mind
) implements SimulationsSceneRequest {
}

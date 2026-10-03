package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record SimulationsDialogueSceneRequest(
        @NotNull LocalDateTime start,
        @NotNull LocalDateTime end,
        @NotBlank String type,
        @NotBlank String place,
        @Schema(nullable = true)
        String placeCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotEmpty List<Long> with,
        @Valid @NotNull List<SimulationsLineRequest> lines
) implements SimulationsSceneRequest {
}

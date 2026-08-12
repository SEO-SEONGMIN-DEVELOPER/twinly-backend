package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record SimulationsDialogueSceneRequest(
        @NotNull LocalTime start,
        @NotNull LocalTime end,
        @NotBlank String type,
        @NotBlank String place,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotEmpty List<Long> with,
        @Valid @NotNull List<SimulationsLineRequest> lines
) implements SimulationsSceneRequest {
}

package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record SimulationsActionSceneRequest(
        @NotNull LocalTime start,
        @NotNull LocalTime end,
        @NotBlank String type,
        @NotBlank String place,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<Long> with,
        @NotBlank String narration,
        String mind
) implements SimulationsSceneRequest {
}

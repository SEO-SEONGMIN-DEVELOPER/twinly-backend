package com.nidus.twinly.simulation.client;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SimulationPreloadRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime grantedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<LocalDate> dates
) {
}

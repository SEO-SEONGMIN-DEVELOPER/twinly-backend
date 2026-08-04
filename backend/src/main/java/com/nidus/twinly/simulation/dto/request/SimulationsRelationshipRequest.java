package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SimulationsRelationshipRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long partnerId,
        @NotNull LocalTime updateTime,
        @NotNull Integer rapport,
        @NotBlank String partnerModel
) {
}

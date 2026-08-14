package com.nidus.twinly.simulation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SimulationsRelationshipRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long partnerId,
        @NotNull LocalDateTime updateTime,
        @NotNull Integer rapport,
        @NotBlank String partnerModel
) {
}

package com.nidus.twinly.world.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record WorldTrackObjectRequest(
        @NotBlank String objectId,
        List<@NotNull @Valid WorldTrackObjectChangeRequest> changes
) {
}

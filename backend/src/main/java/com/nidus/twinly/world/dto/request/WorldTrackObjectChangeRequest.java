package com.nidus.twinly.world.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WorldTrackObjectChangeRequest(
        @NotNull @Pattern(regexp = WorldTrackFormat.TIME) String at,
        @Pattern(regexp = WorldTrackFormat.CHANGED_BY) String by
) {
}

package com.nidus.twinly.world.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WorldTrackGestureRequest(
        @NotNull @Pattern(regexp = WorldTrackFormat.ACTOR_REF) String ref,
        @NotNull @Pattern(regexp = WorldTrackFormat.TIME) String start,
        @NotNull @Pattern(regexp = WorldTrackFormat.TIME) String end
) implements WorldTrackPeriodRequest {
}

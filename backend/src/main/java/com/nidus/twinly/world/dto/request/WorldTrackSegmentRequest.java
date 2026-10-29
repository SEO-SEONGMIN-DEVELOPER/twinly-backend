package com.nidus.twinly.world.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WorldTrackSegmentRequest(
        @NotNull @Pattern(regexp = WorldTrackFormat.TIME) String start,
        @NotNull @Pattern(regexp = WorldTrackFormat.TIME) String end
) implements WorldTrackPeriodRequest {
}

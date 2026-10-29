package com.nidus.twinly.world.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record WorldTrackActorRequest(
        @NotNull @Pattern(regexp = WorldTrackFormat.ACTOR_REF) String ref,
        @NotNull List<@NotNull @Valid WorldTrackSegmentRequest> segments
) {
}

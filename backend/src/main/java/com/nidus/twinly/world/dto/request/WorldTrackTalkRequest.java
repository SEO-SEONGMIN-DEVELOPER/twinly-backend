package com.nidus.twinly.world.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record WorldTrackTalkRequest(
        @NotNull @Pattern(regexp = WorldTrackFormat.TIME) String start,
        @NotNull @Pattern(regexp = WorldTrackFormat.TIME) String end,
        @NotNull List<@NotNull @Pattern(regexp = WorldTrackFormat.ACTOR_REF) String> members
) implements WorldTrackPeriodRequest {
}

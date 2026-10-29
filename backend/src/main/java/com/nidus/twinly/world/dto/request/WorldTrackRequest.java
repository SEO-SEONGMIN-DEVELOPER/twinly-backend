package com.nidus.twinly.world.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record WorldTrackRequest(
        @NotNull String poolNumber,
        @NotNull String date,
        @NotNull String sectorCode,
        @NotNull @Pattern(regexp = WorldTrackFormat.LAYOUT_VERSION) String layoutVersion,
        @JsonProperty(required = true)
        @Pattern(regexp = WorldTrackFormat.VIEWER_USER_ID) String viewerUserId,
        @NotNull List<@NotNull @Valid WorldTrackActorRequest> actors,
        @NotNull List<@NotNull @Valid WorldTrackTalkRequest> talks,
        @NotNull List<@NotNull @Valid WorldTrackGestureRequest> gestures,
        @NotNull List<@NotNull @Valid WorldTrackObjectRequest> objects,
        @NotNull List<@NotNull @Valid WorldTrackEventRequest> events,
        @NotNull List<@NotNull @Valid WorldTrackItemRequest> items
) {
}

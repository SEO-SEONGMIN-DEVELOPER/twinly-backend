package com.nidus.twinly.map.dto.result;

import java.time.OffsetDateTime;

public record MapTwinSceneResult(
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String placeCode,
        String fromPlaceCode,
        String travelMode
) {
}

package com.nidus.twinly.map.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record MapTwinsResult(
        OffsetDateTime serverNow,
        List<MapTwinResult> twins
) {
}

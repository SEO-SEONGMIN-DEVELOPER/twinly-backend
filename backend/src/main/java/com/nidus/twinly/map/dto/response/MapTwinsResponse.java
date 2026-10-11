package com.nidus.twinly.map.dto.response;

import com.nidus.twinly.map.dto.result.MapTwinsResult;

import java.time.OffsetDateTime;
import java.util.List;

public record MapTwinsResponse(
        OffsetDateTime serverNow,
        List<MapTwinResponse> twins
) {

    public static MapTwinsResponse from(MapTwinsResult result) {
        return new MapTwinsResponse(
                result.serverNow(),
                result.twins().stream().map(MapTwinResponse::from).toList()
        );
    }
}

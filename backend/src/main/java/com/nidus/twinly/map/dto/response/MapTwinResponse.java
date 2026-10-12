package com.nidus.twinly.map.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.map.dto.result.MapTwinResult;

import java.util.List;

public record MapTwinResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String displayName,
        List<MapTwinSceneResponse> scenes
) {

    public static MapTwinResponse from(MapTwinResult result) {
        return new MapTwinResponse(
                result.userId(),
                result.displayName(),
                result.scenes().stream().map(MapTwinSceneResponse::from).toList()
        );
    }
}

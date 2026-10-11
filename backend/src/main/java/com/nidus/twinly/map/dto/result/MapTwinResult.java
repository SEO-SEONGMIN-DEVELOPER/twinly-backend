package com.nidus.twinly.map.dto.result;

import java.util.List;

public record MapTwinResult(
        Long userId,
        String displayName,
        List<MapTwinSceneResult> scenes
) {
}

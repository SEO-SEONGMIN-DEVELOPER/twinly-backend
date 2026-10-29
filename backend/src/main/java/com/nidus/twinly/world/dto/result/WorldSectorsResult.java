package com.nidus.twinly.world.dto.result;

import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record WorldSectorsResult(
        String poolNumber,
        LocalDate date,
        String sectorCode,
        String layoutVersion,
        OffsetDateTime serverNow,
        String focusRef,
        List<ObjectNode> actors,
        List<ObjectNode> talks,
        List<ObjectNode> gestures,
        List<ObjectNode> objects,
        List<ObjectNode> events,
        List<ObjectNode> items
) {
}

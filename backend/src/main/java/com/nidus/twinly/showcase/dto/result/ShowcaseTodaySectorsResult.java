package com.nidus.twinly.showcase.dto.result;

import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ShowcaseTodaySectorsResult(
        String poolNumber,
        LocalDate date,
        String sectorCode,
        String layoutVersion,
        OffsetDateTime serverNow,
        String focusRef,
        Long showcaseId,
        Integer refVersion,
        List<ObjectNode> actors,
        List<ObjectNode> talks,
        List<ObjectNode> gestures,
        List<ObjectNode> objects,
        List<ObjectNode> events,
        List<ObjectNode> items
) {
}

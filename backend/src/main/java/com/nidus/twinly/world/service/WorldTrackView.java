package com.nidus.twinly.world.service;

import tools.jackson.databind.node.ObjectNode;

import java.util.List;

public record WorldTrackView(
        String poolNumber,
        String layoutVersion,
        List<ObjectNode> actors,
        List<ObjectNode> talks,
        List<ObjectNode> gestures,
        List<ObjectNode> objects,
        List<ObjectNode> events,
        List<ObjectNode> items
) {
}

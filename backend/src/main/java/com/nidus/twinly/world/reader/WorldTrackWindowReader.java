package com.nidus.twinly.world.reader;

import com.nidus.twinly.world.domain.TrackWindow;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

@Component
public class WorldTrackWindowReader {

    private static final String POOL_NUMBER = "poolNumber";
    private static final String LAYOUT_VERSION = "layoutVersion";
    private static final String ACTORS = "actors";
    private static final String TALKS = "talks";
    private static final String GESTURES = "gestures";
    private static final String OBJECTS = "objects";
    private static final String EVENTS = "events";
    private static final String ITEMS = "items";
    private static final String SEGMENTS = "segments";
    private static final String START = "start";
    private static final String END = "end";

    private final ObjectMapper objectMapper;
    private final ObjectReader elementReader;

    public WorldTrackWindowReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.elementReader = objectMapper.readerFor(JsonNode.class).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public WorldTrackSlice read(byte[] compressed, TrackWindow window) {
        String poolNumber = null;
        String layoutVersion = null;
        List<ObjectNode> actors = new ArrayList<>();
        List<ObjectNode> talks = new ArrayList<>();
        List<ObjectNode> gestures = new ArrayList<>();
        List<ObjectNode> objects = new ArrayList<>();
        List<ObjectNode> events = new ArrayList<>();
        List<ObjectNode> items = new ArrayList<>();
        Predicate<ObjectNode> inWindow = node -> overlaps(node, window);

        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed));
             JsonParser parser = objectMapper.createParser(in)) {
            parser.nextToken();
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                switch (name) {
                    case POOL_NUMBER -> poolNumber = elementReader.<JsonNode>readValue(parser).asString();
                    case LAYOUT_VERSION -> layoutVersion = elementReader.<JsonNode>readValue(parser).asString();
                    case ACTORS -> readEach(parser, actor -> keepSegmentsInWindow(actor, window), actors);
                    case TALKS -> readEach(parser, inWindow, talks);
                    case GESTURES -> readEach(parser, inWindow, gestures);
                    case ITEMS -> readEach(parser, inWindow, items);
                    case OBJECTS -> readEach(parser, node -> true, objects);
                    case EVENTS -> readEach(parser, node -> true, events);
                    default -> parser.skipChildren();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("저장된 세계 트랙을 풀지 못했습니다.", e);
        }

        return new WorldTrackSlice(poolNumber, layoutVersion, actors, talks, gestures, objects, events, items);
    }

    public static boolean overlaps(JsonNode node, TrackWindow window) {
        return window.overlaps(time(node, START), time(node, END));
    }

    public static LocalDateTime time(JsonNode node, String field) {
        return LocalDateTime.parse(node.path(field).asString());
    }

    private void readEach(JsonParser parser, Predicate<ObjectNode> keep, List<ObjectNode> into) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            parser.skipChildren();
            return;
        }

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            JsonNode node = elementReader.readValue(parser);
            if (node instanceof ObjectNode element && keep.test(element)) {
                into.add(element);
            }
        }
    }

    private boolean keepSegmentsInWindow(ObjectNode actor, TrackWindow window) {
        ArrayNode kept = objectMapper.createArrayNode();
        for (JsonNode segment : actor.path(SEGMENTS)) {
            if (overlaps(segment, window)) {
                kept.add(segment);
            }
        }

        if (kept.isEmpty()) {
            return false;
        }

        actor.set(SEGMENTS, kept);
        return true;
    }
}

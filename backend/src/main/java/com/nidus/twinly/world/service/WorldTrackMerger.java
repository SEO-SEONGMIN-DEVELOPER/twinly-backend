package com.nidus.twinly.world.service;

import com.nidus.twinly.common.logging.WarnLog;
import com.nidus.twinly.world.domain.TrackWindow;
import com.nidus.twinly.world.reader.WorldTrackSlice;
import com.nidus.twinly.world.reader.WorldTrackWindowReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorldTrackMerger {

    private static final String REF = "ref";
    private static final String OWNER = "owner";
    private static final String CARD_ID = "cardId";
    private static final String PLACE_CODE = "placeCode";
    private static final String START = "start";
    private static final String EXTRAS = "extras";
    private static final String OBJECT_ID = "objectId";
    private static final String INITIAL = "initial";
    private static final String CHANGES = "changes";
    private static final String AT = "at";
    private static final String STATE = "state";
    private static final String BY = "by";
    private static final String CARD_REF_PREFIX = "card:";

    private final ObjectMapper objectMapper;

    public WorldTrackView merge(WorldTrackSlice publicTrack, WorldTrackSlice personalTrack, String viewerRef, Set<String> speakerRefs, TrackWindow window) {
        if (publicTrack == null) {
            return windowed(personalTrack, personalTrack.actors(), personalTrack.gestures(), personalTrack.items(), personalTrack.events(), personalTrack.objects(), window);
        }

        if (personalTrack == null) {
            return windowed(publicTrack, publicTrack.actors(), publicTrack.gestures(), publicTrack.items(), publicTrack.events(), publicTrack.objects(), window);
        }

        if (!Objects.equals(publicTrack.layoutVersion(), personalTrack.layoutVersion())) {
            WarnLog.log(log, "공용 트랙과 개인 트랙의 배치도 버전이 달라 공용 트랙만 보여 줍니다.",
                    field("viewerRef", viewerRef), field("publicLayoutVersion", publicTrack.layoutVersion()), field("personalLayoutVersion", personalTrack.layoutVersion()));
            return windowed(publicTrack, publicTrack.actors(), publicTrack.gestures(), publicTrack.items(), publicTrack.events(), publicTrack.objects(), window);
        }

        Set<EventKey> publicEventKeys = publicTrack.events().stream().map(EventKey::of).collect(Collectors.toSet());
        List<ObjectNode> addedEvents = personalTrack.events().stream()
                .filter(event -> !publicEventKeys.contains(EventKey.of(event)))
                .toList();
        Set<String> addedExtraRefs = addedEvents.stream()
                .flatMap(event -> stream(event.path(EXTRAS)).map(JsonNode::asString))
                .collect(Collectors.toSet());
        Set<String> changedByRefs = new HashSet<>(addedEvents.stream().map(event -> CARD_REF_PREFIX + text(event, CARD_ID)).toList());
        changedByRefs.add(viewerRef);

        Set<String> publicActorRefs = publicTrack.actors().stream().map(actor -> text(actor, REF)).collect(Collectors.toSet());
        List<ObjectNode> actors = new ArrayList<>(without(publicTrack.actors(), REF, viewerRef));
        for (ObjectNode actor : personalTrack.actors()) {
            String ref = text(actor, REF);
            if (viewerRef.equals(ref)) {
                actors.add(actor);
            } else if (addedExtraRefs.contains(ref) || speakerRefs.contains(ref)) {
                if (!publicActorRefs.contains(ref)) {
                    actors.add(actor);
                } else if (addedExtraRefs.contains(ref)) {
                    WarnLog.log(log, "개인 트랙에서 더할 사건 엑스트라가 공용 트랙에 같은 번호로 있어 더하지 않습니다.",
                            field("viewerRef", viewerRef), field("ref", ref));
                }
            }
        }

        return windowed(
                publicTrack,
                actors,
                Stream.concat(without(publicTrack.gestures(), REF, viewerRef).stream(), only(personalTrack.gestures(), REF, viewerRef).stream()).toList(),
                Stream.concat(without(publicTrack.items(), OWNER, viewerRef).stream(), only(personalTrack.items(), OWNER, viewerRef).stream()).toList(),
                Stream.concat(publicTrack.events().stream(), addedEvents.stream()).toList(),
                mergeObjects(publicTrack.objects(), personalTrack.objects(), changedByRefs),
                window
        );
    }

    private WorldTrackView windowed(WorldTrackSlice base,
                                    List<ObjectNode> actors,
                                    List<ObjectNode> gestures,
                                    List<ObjectNode> items,
                                    List<ObjectNode> events,
                                    List<ObjectNode> objects,
                                    TrackWindow window) {
        return new WorldTrackView(
                base.poolNumber(),
                base.layoutVersion(),
                actors,
                base.talks(),
                gestures,
                objects.stream().map(object -> windowObject(object, window)).toList(),
                events.stream().filter(event -> WorldTrackWindowReader.overlaps(event, window)).toList(),
                items
        );
    }

    private List<ObjectNode> mergeObjects(List<ObjectNode> publicObjects, List<ObjectNode> personalObjects, Set<String> changedByRefs) {
        Map<String, ObjectNode> personalById = new HashMap<>();
        personalObjects.forEach(object -> personalById.putIfAbsent(text(object, OBJECT_ID), object));

        for (ObjectNode object : publicObjects) {
            ObjectNode personal = personalById.get(text(object, OBJECT_ID));
            if (personal == null) {
                continue;
            }

            List<JsonNode> added = stream(personal.path(CHANGES))
                    .filter(change -> changedByRefs.contains(change.path(BY).asString()))
                    .toList();
            if (added.isEmpty()) {
                continue;
            }

            ArrayNode changes = objectMapper.createArrayNode();
            stream(object.path(CHANGES)).forEach(changes::add);
            added.forEach(changes::add);
            object.set(CHANGES, changes);
        }

        return publicObjects;
    }

    private ObjectNode windowObject(ObjectNode object, TrackWindow window) {
        JsonNode changes = object.get(CHANGES);
        if (changes == null || changes.isNull()) {
            object.remove(CHANGES);
            return object;
        }

        List<JsonNode> sorted = new ArrayList<>(stream(changes).toList());
        sorted.sort(Comparator.comparing(change -> WorldTrackWindowReader.time(change, AT)));

        JsonNode state = object.get(INITIAL);
        ArrayNode inWindow = objectMapper.createArrayNode();
        for (JsonNode change : sorted) {
            LocalDateTime at = WorldTrackWindowReader.time(change, AT);
            if (window.isAtOrBeforeStart(at)) {
                state = change.get(STATE);
            } else if (window.containsStrictly(at)) {
                inWindow.add(change);
            }
        }

        if (state != null) {
            object.set(INITIAL, state);
        }
        object.set(CHANGES, inWindow);
        return object;
    }

    private static List<ObjectNode> without(List<ObjectNode> nodes, String field, String ref) {
        return nodes.stream().filter(node -> !ref.equals(text(node, field))).toList();
    }

    private static List<ObjectNode> only(List<ObjectNode> nodes, String field, String ref) {
        return nodes.stream().filter(node -> ref.equals(text(node, field))).toList();
    }

    private static Stream<JsonNode> stream(JsonNode array) {
        List<JsonNode> elements = new ArrayList<>();
        array.forEach(elements::add);
        return elements.stream();
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString();
    }

    private record EventKey(String cardId, String placeCode, LocalDateTime start) {

        private static EventKey of(JsonNode event) {
            return new EventKey(text(event, CARD_ID), text(event, PLACE_CODE), WorldTrackWindowReader.time(event, START));
        }
    }
}

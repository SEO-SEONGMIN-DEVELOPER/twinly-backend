package com.nidus.twinly.world.service;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Component
public class WorldTrackPublisher {

    public static final String USER_REF_PREFIX = "user:";
    private static final String KST_OFFSET = "+09:00";
    private static final String REF = "ref";
    private static final String DISPLAY_NAME = "displayName";
    private static final String GENDER = "gender";
    private static final String SEGMENTS = "segments";
    private static final String MEMBERS = "members";
    private static final String OWNER = "owner";
    private static final String EXTRAS = "extras";
    private static final String CHANGES = "changes";
    private static final String BY = "by";
    private static final String AT = "at";
    private static final String START = "start";
    private static final String END = "end";

    public void publish(WorldTrackView view, Function<String, WorldActorProfile> profileByUserRef, UnaryOperator<String> userRefMapper) {
        for (ObjectNode actor : view.actors()) {
            String ref = actor.path(REF).asString();
            if (ref.startsWith(USER_REF_PREFIX)) {
                WorldActorProfile profile = profileByUserRef.apply(ref);
                actor.put(DISPLAY_NAME, profile.displayName());
                actor.put(GENDER, profile.gender());
            }
            renameField(actor, REF, userRefMapper);
            actor.path(SEGMENTS).forEach(segment -> toKstPeriod((ObjectNode) segment));
        }

        view.talks().forEach(talk -> {
            renameArray(talk, MEMBERS, userRefMapper);
            toKstPeriod(talk);
        });
        periods(view.gestures(), REF, userRefMapper);
        periods(view.items(), OWNER, userRefMapper);
        view.events().forEach(event -> {
            renameArray(event, EXTRAS, userRefMapper);
            toKstPeriod(event);
        });
        view.objects().forEach(object -> object.path(CHANGES).forEach(change -> {
            renameField((ObjectNode) change, BY, userRefMapper);
            toKst((ObjectNode) change, AT);
        }));
    }

    private void periods(List<ObjectNode> nodes, String refField, UnaryOperator<String> userRefMapper) {
        nodes.forEach(node -> {
            renameField(node, refField, userRefMapper);
            toKstPeriod(node);
        });
    }

    private void renameField(ObjectNode node, String field, UnaryOperator<String> userRefMapper) {
        JsonNode value = node.get(field);
        if (value != null && value.isString() && value.asString().startsWith(USER_REF_PREFIX)) {
            node.put(field, userRefMapper.apply(value.asString()));
        }
    }

    private void renameArray(ObjectNode node, String field, UnaryOperator<String> userRefMapper) {
        if (!(node.get(field) instanceof ArrayNode refs)) {
            return;
        }

        for (int i = 0; i < refs.size(); i++) {
            String ref = refs.get(i).asString();
            if (ref.startsWith(USER_REF_PREFIX)) {
                refs.set(i, userRefMapper.apply(ref));
            }
        }
    }

    private void toKstPeriod(ObjectNode node) {
        toKst(node, START);
        toKst(node, END);
    }

    private void toKst(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value != null && value.isString()) {
            node.put(field, value.asString() + KST_OFFSET);
        }
    }
}

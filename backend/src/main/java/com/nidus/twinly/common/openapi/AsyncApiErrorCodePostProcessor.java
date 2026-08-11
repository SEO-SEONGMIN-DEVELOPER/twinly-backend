package com.nidus.twinly.common.openapi;

import com.nidus.twinly.common.websocket.domain.WebSocketErrorCode;
import io.github.springwolf.core.asyncapi.components.postprocessors.SchemasPostProcessor;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AsyncApiErrorCodePostProcessor implements SchemasPostProcessor {

    private static final String SCHEMA_TITLE = WebSocketErrorCode.class.getSimpleName();

    @Override
    public void process(Schema schema, Map<String, Schema> definitions, String contentType) {
        removeServerOnlyValues(schema);

        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        properties.values().forEach(this::removeServerOnlyValues);
    }

    @SuppressWarnings("unchecked")
    private void removeServerOnlyValues(Schema schema) {
        if (!SCHEMA_TITLE.equals(schema.getTitle()) || schema.getEnum() == null) {
            return;
        }

        List<String> serverOnly = WebSocketErrorCode.serverOnlyNames();
        schema.setEnum(((List<Object>) schema.getEnum()).stream()
                .filter(value -> !serverOnly.contains(String.valueOf(value)))
                .toList());
    }
}

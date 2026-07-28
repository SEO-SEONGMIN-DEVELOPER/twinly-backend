package com.nidus.twinly.common.openapi;

import io.github.springwolf.core.asyncapi.components.postprocessors.SchemasPostProcessor;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class AsyncApiSchemaPostProcessor implements SchemasPostProcessor {

    @Override
    public void process(Schema schema, Map<String, Schema> definitions, String contentType) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null || properties.isEmpty()) {
            return;
        }

        schema.setRequired(new ArrayList<>(properties.keySet()));
        properties.values().forEach(this::keepNullable);
    }

    private void keepNullable(Schema<?> property) {
        if (!Boolean.TRUE.equals(property.getNullable()) || property.getType() == null) {
            return;
        }

        property.setTypes(new LinkedHashSet<>(List.of(property.getType())));
    }
}

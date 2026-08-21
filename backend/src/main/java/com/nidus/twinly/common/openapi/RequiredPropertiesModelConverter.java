package com.nidus.twinly.common.openapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RequiredPropertiesModelConverter implements ModelConverter {

    private static final Set<JsonInclude.Include> NULL_OMITTING_INCLUSIONS = Set.of(
            JsonInclude.Include.NON_NULL,
            JsonInclude.Include.NON_ABSENT,
            JsonInclude.Include.NON_EMPTY);

    private final ObjectMapper mapper = Json.mapper();

    @Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (resolved == null || type.getType() == null) {
            return resolved;
        }

        Schema target = resolved.get$ref() != null
                ? context.getDefinedModels().get(simpleName(resolved.get$ref()))
                : resolved;
        if (target == null || target.getProperties() == null || target.getProperties().isEmpty()) {
            return resolved;
        }

        Set<String> optionalProperties = optionalProperties(type);
        List<String> required = ((Map<String, Schema>) target.getProperties()).keySet().stream()
                .filter(name -> !optionalProperties.contains(name))
                .toList();
        target.setRequired(required.isEmpty() ? null : required);

        return resolved;
    }

    private Set<String> optionalProperties(AnnotatedType type) {
        BeanDescription beanDescription = mapper.getSerializationConfig().introspect(mapper.constructType(type.getType()));
        JsonInclude.Value classInclusion = beanDescription.findPropertyInclusion(JsonInclude.Value.empty());

        return beanDescription.findProperties().stream()
                .filter(property -> omitsNull(JsonInclude.Value.merge(classInclusion, property.findInclusion())))
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toSet());
    }

    private boolean omitsNull(JsonInclude.Value inclusion) {
        return NULL_OMITTING_INCLUSIONS.contains(inclusion.getValueInclusion());
    }

    private String simpleName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }
}

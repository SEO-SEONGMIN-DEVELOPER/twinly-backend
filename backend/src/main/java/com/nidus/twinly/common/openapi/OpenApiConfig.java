package com.nidus.twinly.common.openapi;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final String JWT_SCHEME = "jwtAuth";
    private static final String ANON_SESSION_SCHEME = "anonSessionAuth";

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserInfo.class, AnonSessionSnapshot.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(JWT_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSecuritySchemes(ANON_SESSION_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")));
    }

    @Bean
    public OperationCustomizer securityOperationCustomizer() {
        return (operation, handlerMethod) -> {
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(CurrentUser.class)) {
                    operation.addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
                } else if (parameter.hasParameterAnnotation(CurrentAnonSession.class)) {
                    operation.addSecurityItem(new SecurityRequirement().addList(ANON_SESSION_SCHEME));
                }
            }

            return operation;
        };
    }

    @Bean
    public OperationCustomizer commonErrorResponsesCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }

            addErrorResponseIfAbsent(responses, "400", "INVALID_REQUEST");

            boolean requiresAuth = false;
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(CurrentUser.class)
                        || parameter.hasParameterAnnotation(CurrentAnonSession.class)) {
                    requiresAuth = true;
                    break;
                }
            }
            if (requiresAuth) {
                addErrorResponseIfAbsent(responses, "401", "UNAUTHORIZED");
            }

            addErrorResponseIfAbsent(responses, "500", "INTERNAL_ERROR");

            return operation;
        };
    }

    private void addErrorResponseIfAbsent(ApiResponses responses, String status, String description) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, new ApiResponse().description(description));
        }
    }

    @Bean
    public PropertyCustomizer jsonFormatStringPropertyCustomizer() {
        return (schema, type) -> {
            Annotation[] annotations = type.getCtxAnnotations();
            if (annotations == null) {
                return schema;
            }

            for (Annotation annotation : annotations) {
                if (annotation instanceof JsonFormat jsonFormat
                        && jsonFormat.shape() == JsonFormat.Shape.STRING) {
                    StringSchema stringSchema = new StringSchema();
                    if (schema.getDescription() != null) {
                        stringSchema.setDescription(schema.getDescription());
                    }
                    if (schema.getExample() != null) {
                        stringSchema.setExample(schema.getExample());
                    }
                    if (schema.getNullable() != null) {
                        stringSchema.setNullable(schema.getNullable());
                    }
                    return stringSchema;
                }
            }

            return schema;
        };
    }

    @Bean
    public OpenApiCustomizer allResponseFieldsRequiredCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null || components.getSchemas() == null) {
                return;
            }
            Map<String, Schema> schemas = components.getSchemas();

            Deque<String> queue = new ArrayDeque<>();
            schemas.keySet().stream()
                    .filter(name -> name.endsWith("Response"))
                    .forEach(queue::add);

            Set<String> visited = new HashSet<>();
            while (!queue.isEmpty()) {
                String name = queue.poll();
                if (!visited.add(name)) {
                    continue;
                }
                Schema schema = schemas.get(name);
                if (schema == null) {
                    continue;
                }
                if (schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
                collectReferencedSchemaNames(schema, queue);
            }
        };
    }

    private void collectReferencedSchemaNames(Schema schema, Deque<String> queue) {
        if (schema == null) {
            return;
        }
        enqueueRefName(schema.get$ref(), queue);

        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(property -> collectReferencedSchemaNames((Schema) property, queue));
        }
        if (schema.getItems() != null) {
            collectReferencedSchemaNames(schema.getItems(), queue);
        }
        if (schema.getAdditionalProperties() instanceof Schema additionalProperties) {
            collectReferencedSchemaNames(additionalProperties, queue);
        }
        collectComposedSchemaNames(schema.getAllOf(), queue);
        collectComposedSchemaNames(schema.getOneOf(), queue);
        collectComposedSchemaNames(schema.getAnyOf(), queue);
    }

    private void collectComposedSchemaNames(List<Schema> composedSchemas, Deque<String> queue) {
        if (composedSchemas != null) {
            composedSchemas.forEach(composed -> collectReferencedSchemaNames(composed, queue));
        }
    }

    private void enqueueRefName(String ref, Deque<String> queue) {
        if (ref != null) {
            queue.add(ref.substring(ref.lastIndexOf('/') + 1));
        }
    }
}

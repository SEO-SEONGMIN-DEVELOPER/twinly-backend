package com.nidus.twinly.app.openapi;

import com.nidus.twinly.app.dto.response.AppUpdateRequiredResponse;
import com.nidus.twinly.app.dto.response.MaintenanceResponse;
import com.nidus.twinly.app.filter.AppBlockFilter;
import com.nidus.twinly.common.web.ErrorCode;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class AppBlockOpenApiConfig {

    static final String MAINTENANCE_RESPONSE = "Maintenance";
    static final String UPGRADE_REQUIRED_RESPONSE = "UpgradeRequired";
    static final String PLATFORM_PARAMETER = "XAppPlatform";
    static final String VERSION_PARAMETER = "XAppVersion";

    private static final String APP_PATH_PREFIX = "/api/";
    private static final String COMPONENT_RESPONSES_REF = "#/components/responses/";
    private static final String COMPONENT_PARAMETERS_REF = "#/components/parameters/";

    @Bean
    public OpenApiCustomizer appBlockOpenApiCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }

            ModelConverters converters = ModelConverters.getInstance(openApi.getSpecVersion() == SpecVersion.V31);

            components.addResponses(MAINTENANCE_RESPONSE,
                    blockResponse(converters, components, MaintenanceResponse.class, ErrorCode.MAINTENANCE.name()));
            components.addResponses(UPGRADE_REQUIRED_RESPONSE,
                    blockResponse(converters, components, AppUpdateRequiredResponse.class, ErrorCode.APP_UPDATE_REQUIRED.name()));

            components.addParameters(PLATFORM_PARAMETER, new HeaderParameter()
                    .name(AppBlockFilter.PLATFORM_HEADER)
                    .required(false)
                    .schema(new StringSchema()._enum(List.of("ios", "android"))));
            components.addParameters(VERSION_PARAMETER, new HeaderParameter()
                    .name(AppBlockFilter.VERSION_HEADER)
                    .required(false)
                    .schema(new StringSchema().pattern("^\\d+\\.\\d+\\.\\d+$").example("0.1.2")));

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                if (path.startsWith(APP_PATH_PREFIX)) {
                    pathItem.readOperations().forEach(this::applyToOperation);
                }
            });
        };
    }

    private void applyToOperation(Operation operation) {
        List<Parameter> parameters = operation.getParameters() != null
                ? new ArrayList<>(operation.getParameters())
                : new ArrayList<>();
        parameters.add(new Parameter().$ref(COMPONENT_PARAMETERS_REF + PLATFORM_PARAMETER));
        parameters.add(new Parameter().$ref(COMPONENT_PARAMETERS_REF + VERSION_PARAMETER));
        operation.setParameters(parameters);

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        addBlockResponse(responses, "426", UPGRADE_REQUIRED_RESPONSE, ErrorCode.APP_UPDATE_REQUIRED);
        addBlockResponse(responses, "503", MAINTENANCE_RESPONSE, ErrorCode.MAINTENANCE);
    }

    private void addBlockResponse(ApiResponses responses, String status, String componentName, ErrorCode errorCode) {
        ApiResponse existing = responses.get(status);
        if (existing == null) {
            responses.addApiResponse(status, new ApiResponse().$ref(COMPONENT_RESPONSES_REF + componentName));
            return;
        }

        String description = existing.getDescription();
        if (description == null || description.isBlank()) {
            existing.setDescription(errorCode.name());
            return;
        }

        if (!List.of(description.split(",\\s*")).contains(errorCode.name())) {
            existing.setDescription(description + ", " + errorCode.name());
        }
    }

    private ApiResponse blockResponse(ModelConverters converters, Components components, Class<?> bodyType, String description) {
        ResolvedSchema resolved = converters.resolveAsResolvedSchema(new AnnotatedType(bodyType).resolveAsRef(true));
        if (resolved.referencedSchemas != null) {
            resolved.referencedSchemas.forEach(components::addSchemas);
        }

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(resolved.schema)));
    }
}

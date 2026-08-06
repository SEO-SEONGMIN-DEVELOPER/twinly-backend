package com.nidus.twinly.common.openapi;

import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;

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
                if (isPrincipalOfType(parameter, UserInfo.class)) {
                    operation.addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
                } else if (isPrincipalOfType(parameter, AnonSessionSnapshot.class)) {
                    operation.addSecurityItem(new SecurityRequirement().addList(ANON_SESSION_SCHEME));
                }
            }

            return operation;
        };
    }

    private boolean isPrincipalOfType(MethodParameter parameter, Class<?> principalType) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                && principalType.equals(parameter.getParameterType());
    }

    private static final List<ErrorCode> USER_AUTH_401 = List.of(
            ErrorCode.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, ErrorCode.WITHDRAWN_USER);

    private static final List<ErrorCode> ANON_AUTH_401 = List.of(
            ErrorCode.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, ErrorCode.INVALID_ANON_SESSION, ErrorCode.TOKEN_EXPIRED);

    private static final Pattern ERROR_CODE_NAMES = Pattern.compile("[A-Z][A-Z_]*(, ?[A-Z][A-Z_]*)*");

    @Bean
    public OperationCustomizer commonErrorResponsesCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }

            mergeErrorCodes(responses, "400", List.of(ErrorCode.INVALID_REQUEST));
            mergeErrorCodes(responses, "500", List.of(ErrorCode.INTERNAL_ERROR));

            if (operation.getRequestBody() != null) {
                mergeErrorCodes(responses, "415", List.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
            }

            List<ErrorCode> auth401 = resolveAuth401(handlerMethod);
            if (!auth401.isEmpty()) {
                mergeErrorCodes(responses, "401", auth401);
            }

            return operation;
        };
    }

    @Bean
    public OperationCustomizer defaultSuccessResponseCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }

            boolean hasSuccess = responses.keySet().stream().anyMatch(status -> status.startsWith("2"));
            if (hasSuccess) {
                return operation;
            }

            responses.addApiResponse(successStatus(handlerMethod), successResponse(handlerMethod));

            return operation;
        };
    }

    private String successStatus(HandlerMethod handlerMethod) {
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ResponseStatus.class);

        return String.valueOf(responseStatus != null ? responseStatus.code().value() : HttpStatus.OK.value());
    }

    private ApiResponse successResponse(HandlerMethod handlerMethod) {
        ApiResponse response = new ApiResponse().description("OK");

        Type returnType = handlerMethod.getMethod().getGenericReturnType();
        if (returnType == void.class || returnType == Void.class) {
            return response;
        }

        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(returnType).resolveAsRef(true));
        if (resolved == null || resolved.schema == null) {
            return response;
        }

        return response.content(new Content()
                .addMediaType(MediaType.APPLICATION_JSON_VALUE, new io.swagger.v3.oas.models.media.MediaType().schema(resolved.schema)));
    }

    private List<ErrorCode> resolveAuth401(HandlerMethod handlerMethod) {
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            if (isPrincipalOfType(parameter, UserInfo.class)) {
                return USER_AUTH_401;
            }
            if (isPrincipalOfType(parameter, AnonSessionSnapshot.class)) {
                return ANON_AUTH_401;
            }
        }

        return List.of();
    }

    private void mergeErrorCodes(ApiResponses responses, String status, List<ErrorCode> errorCodes) {
        Set<String> merged = new LinkedHashSet<>();
        errorCodes.forEach(errorCode -> merged.add(errorCode.name()));

        ApiResponse existing = responses.get(status);
        if (existing == null) {
            responses.addApiResponse(status, new ApiResponse().description(String.join(", ", merged)));
            return;
        }

        String description = existing.getDescription();
        if (description != null && ERROR_CODE_NAMES.matcher(description).matches()) {
            for (String name : description.split(",")) {
                merged.add(name.trim());
            }
        }

        existing.setDescription(String.join(", ", merged));
    }

    @Bean
    public OpenApiCustomizer allFieldsRequiredCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null || components.getSchemas() == null) {
                return;
            }

            for (Schema schema : components.getSchemas().values()) {
                if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            }
        };
    }

    @Bean
    public OpenApiCustomizer nullableRefOneOfCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null || components.getSchemas() == null) {
                return;
            }

            for (Schema schema : components.getSchemas().values()) {
                Map<String, Schema> properties = schema.getProperties();
                if (properties != null) {
                    properties.replaceAll((name, property) -> toOneOfIfNullableRef(property));
                }
            }
        };
    }

    private Schema toOneOfIfNullableRef(Schema property) {
        if (property.get$ref() == null || !isNullType(property)) {
            return property;
        }

        Schema nullSchema = new Schema<>();
        nullSchema.addType("null");

        Schema oneOf = new Schema<>().oneOf(List.of(new Schema<>().$ref(property.get$ref()), nullSchema));
        if (property.getDescription() != null) {
            oneOf.setDescription(property.getDescription());
        }

        return oneOf;
    }

    private boolean isNullType(Schema property) {
        if ("null".equals(property.getType())) {
            return true;
        }

        Set<String> types = property.getTypes();

        return types != null && types.contains("null");
    }
}

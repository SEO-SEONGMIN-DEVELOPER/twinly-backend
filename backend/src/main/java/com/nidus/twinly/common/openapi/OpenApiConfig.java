package com.nidus.twinly.common.openapi;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;

import java.lang.annotation.Annotation;

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
}

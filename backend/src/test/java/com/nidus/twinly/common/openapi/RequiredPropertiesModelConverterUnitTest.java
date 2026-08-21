package com.nidus.twinly.common.openapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.springwolf.asyncapi.v3.model.components.ComponentSchema;
import io.github.springwolf.core.asyncapi.schemas.SwaggerSchemaMapper;
import io.github.springwolf.core.configuration.properties.SpringwolfConfigProperties;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredPropertiesModelConverterUnitTest {

    record Nested(String value) {
    }

    record Sample(
            String always,
            @JsonInclude(JsonInclude.Include.NON_NULL) String omittedWhenNull,
            @Schema(nullable = true) String nullableButPresent,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> omittedWhenEmpty,
            Nested nested
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AllOptional(String first, String second) {
    }

    ModelConverters modelConverters;

    @BeforeEach
    void setUp() {
        modelConverters = new ModelConverters();
        modelConverters.addConverter(new RequiredPropertiesModelConverter());
    }

    @Test
    @DisplayName("기본은 모든 프로퍼티가 required이고 null 불가이며, @JsonInclude로 null을 생략하는 프로퍼티만 required에서 빠진다")
    void required_excludes_null_omitting_properties() {
        // given: 항상 있는 필드, NON_NULL 필드, nullable 필드, NON_EMPTY 필드, 중첩 객체가 섞인 레코드

        // when: swagger-core 로 스키마를 만든다
        Map<String, io.swagger.v3.oas.models.media.Schema> schemas = modelConverters.readAll(new AnnotatedType(Sample.class));

        // then: NON_NULL·NON_EMPTY 는 빠지고 나머지는 required, nullable 은 @Schema 가 붙은 필드만
        io.swagger.v3.oas.models.media.Schema sample = schemas.get("Sample");
        assertThat(sample.getRequired()).containsExactlyInAnyOrder("always", "nullableButPresent", "nested");

        Map<String, io.swagger.v3.oas.models.media.Schema> properties = sample.getProperties();
        assertThat(properties.get("nullableButPresent").getNullable()).isTrue();
        assertThat(properties.get("always").getNullable()).isNull();
        assertThat(properties.get("omittedWhenNull").getNullable()).isNull();

        // then: 중첩 객체도 별도 컴포넌트로 같은 규칙을 적용받는다
        assertThat(schemas.get("Nested").getRequired()).containsExactly("value");
    }

    @Test
    @DisplayName("resolveAsRef 로 $ref 만 돌려받는 경로에서도 컨텍스트에 등록된 실제 모델에 required 가 채워진다")
    void required_is_applied_when_resolver_returns_ref() {
        // given: springdoc 응답 스키마와 같이 resolveAsRef(true) 로 조회

        // when: 반환값은 $ref 이고 실제 모델은 referencedSchemas 에 있다
        ResolvedSchema resolved = modelConverters.resolveAsResolvedSchema(new AnnotatedType(Sample.class).resolveAsRef(true));

        // then: $ref 뒤의 모델에 required 가 반영됨
        assertThat(resolved.schema.get$ref()).endsWith("/Sample");
        assertThat(resolved.referencedSchemas.get("Sample").getRequired())
                .containsExactlyInAnyOrder("always", "nullableButPresent", "nested");
    }

    @Test
    @DisplayName("클래스에 @JsonInclude(NON_NULL) 이 붙으면 모든 프로퍼티가 선택이 되어 required 를 비운다")
    void class_level_inclusion_makes_every_property_optional() {
        // given: 클래스 단위 NON_NULL

        // when: 스키마 생성
        Map<String, io.swagger.v3.oas.models.media.Schema> schemas = modelConverters.readAll(new AnnotatedType(AllOptional.class));

        // then: 빈 required 배열 대신 required 자체를 두지 않는다 (OpenAPI 3.0 은 빈 required 를 허용하지 않음)
        assertThat(schemas.get("AllOptional").getRequired()).isNull();
    }

    @Test
    @DisplayName("Springwolf 매퍼를 거쳐도 required 는 유지되고 nullable 은 type 에 null 이 추가된 형태로 표현된다")
    void springwolf_mapping_keeps_required_and_nullable() {
        // given: swagger-core 스키마를 Springwolf AsyncAPI 스키마로 변환
        Map<String, io.swagger.v3.oas.models.media.Schema> schemas = modelConverters.readAll(new AnnotatedType(Sample.class));
        SwaggerSchemaMapper mapper = new SwaggerSchemaMapper(new SpringwolfConfigProperties());

        // when: 변환
        Map<String, ComponentSchema> mapped = mapper.mapSchemasMap(schemas);

        // then: required 그대로, nullable 필드만 type 에 "null" 포함
        io.github.springwolf.asyncapi.v3.model.schema.SchemaObject sample = mapped.get("Sample").getSchema();
        assertThat(sample.getRequired()).containsExactlyInAnyOrder("always", "nullableButPresent", "nested");

        io.github.springwolf.asyncapi.v3.model.schema.SchemaObject nullable =
                ((ComponentSchema) sample.getProperties().get("nullableButPresent")).getSchema();
        io.github.springwolf.asyncapi.v3.model.schema.SchemaObject always =
                ((ComponentSchema) sample.getProperties().get("always")).getSchema();
        assertThat(nullable.getType()).contains("null");
        assertThat(always.getType()).doesNotContain("null");
    }
}

package com.nidus.twinly.common.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeDocsControllerTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(ErrorCodeDocsController.class);

    @Test
    @DisplayName("API 문서를 여는 환경에서만 오류 명세 엔드포인트가 등록된다")
    void registered_only_when_api_docs_enabled() {
        contextRunner.withPropertyValues("springdoc.api-docs.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ErrorCodeDocsController.class));
    }

    @Test
    @DisplayName("API 문서가 꺼진 환경에서는 오류 명세 엔드포인트가 등록되지 않는다")
    void not_registered_when_api_docs_disabled() {
        contextRunner.withPropertyValues("springdoc.api-docs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ErrorCodeDocsController.class));
    }

    @Test
    @DisplayName("설정 자체가 없으면 문서가 열리지 않는 쪽으로 동작한다")
    void not_registered_when_property_missing() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(ErrorCodeDocsController.class));
    }
}

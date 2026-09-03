package com.nidus.twinly.app.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppBlockOpenApiConfigUnitTest {

    private final AppBlockOpenApiConfig config = new AppBlockOpenApiConfig();

    @Test
    @DisplayName("503·426이 없는 /api operation에는 공통 응답을 $ref로 붙인다")
    void addsRefsWhenAbsent() {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("OK")));
        OpenAPI openApi = openApiWith("/api/v1/things", operation);

        config.appBlockOpenApiCustomizer().customise(openApi);

        assertThat(operation.getResponses().get("503").get$ref()).isEqualTo("#/components/responses/Maintenance");
        assertThat(operation.getResponses().get("426").get$ref()).isEqualTo("#/components/responses/UpgradeRequired");
    }

    @Test
    @DisplayName("이미 인라인 503이 있으면 덮어쓰지 않고 코드 이름을 description에 합친다 (예: REALTIME_UNAVAILABLE)")
    void mergesIntoExistingInlineResponse() {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("503", new ApiResponse().description("REALTIME_UNAVAILABLE")));
        OpenAPI openApi = openApiWith("/api/v1/connection-tokens", operation);

        config.appBlockOpenApiCustomizer().customise(openApi);

        ApiResponse merged = operation.getResponses().get("503");
        assertThat(merged.get$ref()).isNull();
        assertThat(merged.getDescription()).isEqualTo("REALTIME_UNAVAILABLE, MAINTENANCE");
    }

    @Test
    @DisplayName("두 번 적용돼도 코드 이름이 중복되지 않는다")
    void idempotentMerge() {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("503", new ApiResponse().description("REALTIME_UNAVAILABLE, MAINTENANCE")));
        OpenAPI openApi = openApiWith("/api/v1/connection-tokens", operation);

        config.appBlockOpenApiCustomizer().customise(openApi);

        assertThat(operation.getResponses().get("503").getDescription()).isEqualTo("REALTIME_UNAVAILABLE, MAINTENANCE");
    }

    @Test
    @DisplayName("/api 밖의 operation은 건드리지 않는다")
    void skipsNonApiPaths() {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("OK")));
        OpenAPI openApi = openApiWith("/admin/app/maintenance", operation);

        config.appBlockOpenApiCustomizer().customise(openApi);

        assertThat(operation.getResponses().containsKey("503")).isFalse();
        assertThat(operation.getParameters()).isNull();
    }

    private static OpenAPI openApiWith(String path, Operation operation) {
        return new OpenAPI().paths(new Paths().addPathItem(path, new PathItem().get(operation)));
    }
}

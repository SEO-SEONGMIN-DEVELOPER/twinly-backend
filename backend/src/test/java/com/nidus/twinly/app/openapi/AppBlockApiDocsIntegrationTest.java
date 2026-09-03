package com.nidus.twinly.app.openapi;

import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class AppBlockApiDocsIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("공통 503·426 응답과 앱 식별 헤더가 components에 한 번 정의된다")
    void components_defineBlockResponsesAndHeaders() throws Exception {
        ResultActions result = mockMvc.perform(get("/docs/openapi").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        result.andExpect(jsonPath("$.components.responses.Maintenance.content['application/json'].schema.$ref",
                        is("#/components/schemas/MaintenanceResponse")))
                .andExpect(jsonPath("$.components.responses.UpgradeRequired.content['application/json'].schema.$ref",
                        is("#/components/schemas/AppUpdateRequiredResponse")))
                .andExpect(jsonPath("$.components.schemas.MaintenanceResponse.required", containsInAnyOrder("code", "message", "until")))
                .andExpect(jsonPath("$.components.schemas.MaintenanceResponse.properties.until.type", hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.AppUpdateRequiredResponse.required",
                        containsInAnyOrder("code", "message", "storeUrl", "minVersion")))
                .andExpect(jsonPath("$.components.schemas.AppUpdateRequiredResponse.properties.storeUrl.type", is("string")))
                .andExpect(jsonPath("$.components.parameters.XAppPlatform.name", is("X-App-Platform")))
                .andExpect(jsonPath("$.components.parameters.XAppPlatform.in", is("header")))
                .andExpect(jsonPath("$.components.parameters.XAppPlatform.schema.enum", containsInAnyOrder("ios", "android")))
                .andExpect(jsonPath("$.components.parameters.XAppVersion.name", is("X-App-Version")));
    }

    @Test
    @DisplayName("/api/** operation에는 503·426 응답과 두 헤더가 $ref로 붙고, /admin/** 에는 붙지 않는다")
    void operations_underApi_referenceBlockResponses() throws Exception {
        ResultActions result = mockMvc.perform(get("/docs/openapi").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        String status = "$.paths['/api/v1/app/status'].get";
        result.andExpect(jsonPath(status + ".responses['200']").exists())
                .andExpect(jsonPath(status + ".responses['503'].$ref", is("#/components/responses/Maintenance")))
                .andExpect(jsonPath(status + ".responses['426'].$ref", is("#/components/responses/UpgradeRequired")))
                .andExpect(jsonPath(status + ".parameters[*].$ref",
                        containsInAnyOrder("#/components/parameters/XAppPlatform", "#/components/parameters/XAppVersion")))
                .andExpect(jsonPath(status + ".security").doesNotExist());

        String refresh = "$.paths['/api/v1/auth/refresh'].post";
        result.andExpect(jsonPath(refresh + ".responses['503'].$ref", is("#/components/responses/Maintenance")));

        String admin = "$.paths['/admin/app/maintenance'].put";
        result.andExpect(jsonPath(admin + ".responses['503']").doesNotExist())
                .andExpect(jsonPath(admin + ".responses['426']").doesNotExist());
    }
}

package com.nidus.twinly.common.security.integration;

import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 경로의 인가 규칙을 고정한다.
 * 특정 API가 아니라 규칙 자체가 관심사이므로, 매핑되지 않은 경로를 써서 "인가를 통과하면 404"를 통과의 증거로 삼는다.
 */
@TestPropertySource(properties = "admin.api-token=" + AdminSecurityRuleIntegrationTest.ADMIN_TOKEN)
class AdminSecurityRuleIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "test-admin-token";

    private static final String ADMIN_PATH = "/admin/ping";

    @Test
    @DisplayName("관리자 경로는 아무 인증이 없으면 401로 끊긴다")
    void adminPath_withoutAnyCredential_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    @DisplayName("관리자 토큰이 틀리면 권한을 얻지 못하고 401로 끊긴다")
    void adminPath_withWrongToken_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_PATH).header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    @DisplayName("규칙 순서 고정: 로그인한 일반 유저는 인증은 되지만 권한이 없어 403이다")
    void adminPath_withUserJwtOnly_returns403() throws Exception {
        User user = saveUser();

        mockMvc.perform(get(ADMIN_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    @DisplayName("올바른 관리자 토큰이면 인가를 통과한다 (경로가 없어 404)")
    void adminPath_withAdminToken_passesAuthorization() throws Exception {
        mockMvc.perform(get(ADMIN_PATH).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));
    }

    @Test
    @DisplayName("필터 순서 고정: 유저 JWT를 함께 보내도 관리자 토큰이 먼저 평가되어 인가를 통과한다")
    void adminPath_withBothCredentials_adminTokenWins() throws Exception {
        User user = saveUser();

        mockMvc.perform(get(ADMIN_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자 토큰은 관리자 경로 밖에서는 아무 권한도 주지 않는다")
    void adminToken_doesNotLeakOutsideAdminPaths() throws Exception {
        mockMvc.perform(get("/api/v1/blocks").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }
}

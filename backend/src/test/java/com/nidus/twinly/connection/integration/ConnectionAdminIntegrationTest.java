package com.nidus.twinly.connection.integration;

import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `POST /admin/connection/draining` 의 HTTP 계약 검증.
 *
 * <p>이 API는 DB를 바꾸지 않고 Redis 릴레이로 전파만 하므로, 여기서는 <b>관리자 인증 · 요청 검증 · 상태 코드</b>를
 * 검증한다. 실제로 접속 중인 클라이언트에게 control 프레임이 도달하는지는 실제 소켓이 필요하므로
 * {@link ConnectionControlWebSocketIntegrationTest} 가 담당한다.
 */
@TestPropertySource(properties = "admin.api-token=" + ConnectionAdminIntegrationTest.ADMIN_TOKEN)
class ConnectionAdminIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "admin-integration-token";

    private static final String ADMIN_DRAINING_PATH = "/admin/connection/draining";

    @Test
    @DisplayName("draining 예고: 관리자 토큰으로 사유와 재연결 대기 시간을 보내면 200을 반환한다")
    void notifyDraining_end_to_end() throws Exception {
        // given: 배포 사유로 3초 후 재연결을 예고하는 요청

        // when & then: 관리자 토큰이 붙으면 통과한다
        mockMvc.perform(post(ADMIN_DRAINING_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"deploy","retryAfterMs":3000}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("draining 예고: retryAfterMs를 생략하면 재연결 시점을 클라이언트에 맡기고 200을 반환한다")
    void notifyDraining_allowsOmittedRetryAfterMs() throws Exception {
        // given: 대기 시간 없이 점검 사유만 보내는 요청 (payload 스펙상 nullable)

        // when & then
        mockMvc.perform(post(ADMIN_DRAINING_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"maintenance"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("draining 예고는 관리자 토큰 없이는 호출할 수 없다")
    void notifyDraining_requiresAdminToken() throws Exception {
        // given: 본문은 정상이지만 관리자 토큰이 없는 요청

        // when & then: 인증 단계에서 막혀 401
        mockMvc.perform(post(ADMIN_DRAINING_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"deploy","retryAfterMs":3000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    @DisplayName("draining 예고 실패: 사유가 없으면 400과 INVALID_REQUEST를 반환한다")
    void notifyDraining_withoutReason_returns400() throws Exception {
        // given: reason 이 빠진 요청 (클라이언트가 행동을 결정할 근거가 없다)

        // when & then
        mockMvc.perform(post(ADMIN_DRAINING_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"retryAfterMs":3000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
    }

    @Test
    @DisplayName("draining 예고 실패: retryAfterMs가 양수가 아니면 400과 INVALID_REQUEST를 반환한다")
    void notifyDraining_withNonPositiveRetryAfterMs_returns400() throws Exception {
        // given: 0 이하가 통과하면 클라이언트가 즉시 재연결을 시도해 재연결 폭풍이 난다

        // when & then
        mockMvc.perform(post(ADMIN_DRAINING_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"deploy","retryAfterMs":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
    }

    @Test
    @DisplayName("draining 예고 실패: 정의되지 않은 사유는 400과 INVALID_REQUEST를 반환한다")
    void notifyDraining_withUnknownReason_returns400() throws Exception {
        // given: 클라이언트가 해석할 수 없는 사유는 파싱 단계에서 걸러야 한다

        // when & then
        mockMvc.perform(post(ADMIN_DRAINING_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"unknown","retryAfterMs":3000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
    }
}

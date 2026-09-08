package com.nidus.twinly.common.security.integration;

import com.jayway.jsonpath.JsonPath;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 인가 규칙이 실제 필터 체인에서 적용되고, 401 응답 형식과 에러 코드가 기존 계약을 유지하는지 고정한다. */
class SecurityRuleIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("공개 API는 토큰 없이도 통과한다")
    void publicEndpoint_passesWithoutToken() throws Exception {
        // when: 공개로 선언한 경로를 토큰 없이 호출
        // then: 인가 규칙에 걸리지 않고 200
        mockMvc.perform(get("/api/v1/legal/policies"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("보호된 API는 토큰이 없으면 인가 단계에서 401 UNAUTHORIZED로 끊긴다")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        // when: 보호된 경로를 토큰 없이 호출
        // then: 401과 함께 응답 본문의 코드·메시지 계약도 유지된다
        mockMvc.perform(get("/api/v1/blocks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getDefaultMessage()));
    }

    @Test
    @DisplayName("보호된 API에 유효한 액세스 토큰이면 인가를 통과해 정상 응답한다")
    void protectedEndpoint_withValidToken_passes() throws Exception {
        // given: 실제 유저를 저장해 유효한 액세스 토큰을 만든다
        User user = saveUser();

        // when: 유효한 토큰으로 보호된 경로 호출
        // then: 인가를 통과해 정상 응답
        mockMvc.perform(get("/api/v1/blocks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰이 유효하지 않으면 UNAUTHORIZED로 뭉뚱그리지 않고 기존 INVALID_TOKEN 코드를 그대로 유지한다")
    void protectedEndpoint_withInvalidToken_keepsOriginalErrorCode() throws Exception {
        // when: JWT 형식이 아닌 토큰으로 호출
        // then: UNAUTHORIZED로 뭉뚱그리지 않고 INVALID_TOKEN 코드를 그대로 내려준다
        mockMvc.perform(get("/api/v1/blocks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TOKEN.name()));
    }

    @Test
    @DisplayName("익명 세션 API는 토큰이 없으면 401 UNAUTHORIZED로 끊긴다")
    void anonSessionEndpoint_withoutToken_returns401() throws Exception {
        // when: 익명 세션 경로를 토큰 없이 호출
        // then: 401 UNAUTHORIZED
        mockMvc.perform(post("/api/v1/onboarding/interests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    @DisplayName("익명 세션 API에 유저 액세스 토큰을 보내면 403이 아니라 기존과 같은 401 INVALID_TOKEN이다")
    void anonSessionEndpoint_withUserToken_returns401InvalidToken() throws Exception {
        // given: 익명 세션이 아니라 정식 유저의 토큰을 준비한다
        User user = saveUser();

        // when: 익명 세션 경로에 유저 토큰을 보낸다
        // then: 역할 불일치를 403이 아니라 기존과 같은 401 INVALID_TOKEN으로 유지한다
        mockMvc.perform(post("/api/v1/onboarding/interests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TOKEN.name()));
    }

    @Test
    @DisplayName("익명 세션 API에 존재하지 않는 세션 토큰이면 INVALID_ANON_SESSION 코드가 그대로 유지된다")
    void anonSessionEndpoint_withUnknownSession_keepsOriginalErrorCode() throws Exception {
        // when: 형식은 맞지만 존재하지 않는 세션 토큰으로 호출
        // then: INVALID_ANON_SESSION 코드가 그대로 유지된다
        mockMvc.perform(post("/api/v1/onboarding/interests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ANON_SESSION.name()));
    }

    @Test
    @DisplayName("공개 온보딩 API는 익명 세션 규칙에서 예외로 빠져 토큰 없이 통과한다")
    void publicOnboardingEndpoints_passWithoutToken() throws Exception {
        // when & then: 익명 세션 규칙의 예외로 선언한 두 경로는 토큰 없이 통과한다
        mockMvc.perform(get("/api/v1/onboarding/survey-questions"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/onboarding/organizations"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효한 익명 세션 토큰이면 ROLE_ANON으로 인가를 통과한다")
    void anonSessionEndpoint_withValidToken_passes() throws Exception {
        // given: 실제 익명 세션을 발급받는다
        String token = JsonPath.read(
                mockMvc.perform(post("/api/v1/anon/start")).andReturn().getResponse().getContentAsString(),
                "$.anonSessionToken");

        // when: 그 토큰으로 익명 세션 경로 호출
        int status = mockMvc.perform(get("/api/v1/onboarding/affiliations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus();

        // then: 비즈니스 검증 결과와 무관하게 인증·인가 단계를 통과했음을 고정한다
        assertThat(status).isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }
}

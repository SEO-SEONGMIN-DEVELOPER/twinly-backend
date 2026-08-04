package com.nidus.twinly.anon.controller;

import com.nidus.twinly.anon.dto.result.AnonStartResult;
import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.nidus.twinly.common.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnonController.class)
@Import(SecurityConfig.class)
class AnonControllerUnitTest {

    private static final UUID TOKEN = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-09T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnonService anonService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @BeforeEach
    void setUp() {
        given(anonService.start()).willReturn(new AnonStartResult(TOKEN, EXPIRES_AT));
    }

    @Test
    @DisplayName("익명 세션 시작 성공 시 201을 반환하고 서비스가 발급한 토큰·만료시각을 응답한다")
    void start_success() throws Exception {
        // when: 인증 없이 익명 세션 시작 API 호출 (인증이 필요 없는 공개 엔드포인트)
        var result = mockMvc.perform(post("/api/v1/anon/start"));

        // then: 항상 새 세션을 만드는 호출이므로 201 + 서비스 결과가 응답 JSON으로 매핑되고 세션 발급을 서비스에 위임
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.anonSessionToken").value(TOKEN.toString()))
                .andExpect(jsonPath("$.expiresAt").exists());
        then(anonService).should().start();
    }

    @Test
    @DisplayName("Authorization 헤더가 붙어 있어도 기존 세션을 조회하지 않고 새 세션을 발급한다")
    void start_ignores_authorization_header() throws Exception {
        // given: 익명 세션 토큰 형식이 아닌 임의의 Authorization 헤더가 함께 전달되는 상황
        String bogusAuthorization = "Bearer not-a-uuid";

        // when: 해당 Authorization 헤더를 붙여 익명 세션 시작 API 호출
        var result = mockMvc.perform(post("/api/v1/anon/start")
                .header("Authorization", bogusAuthorization));

        // then: 201 반환 + 기존 세션 조회(resolveByToken)는 일어나지 않고 새 세션만 발급
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.anonSessionToken").value(TOKEN.toString()));
        then(anonService).should().start();
        then(anonService).should(never()).resolveByToken(any());
    }
}

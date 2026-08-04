package com.nidus.twinly.connection.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.dto.command.ConnectionTokenCommand;
import com.nidus.twinly.connection.dto.result.ConnectionTokenResult;
import com.nidus.twinly.connection.service.ConnectionService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.nidus.twinly.common.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConnectionController.class)
@Import(SecurityConfig.class)
class ConnectionControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConnectionService connectionService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString()))
                .willReturn(new UserInfo(1L));
    }

    @Test
    @DisplayName("연결 토큰 발급 성공 시 201과 티켓 정보를 반환하고 인증 유저 id·커맨드로 서비스를 호출한다")
    void token_success() throws Exception {
        // given: 서비스가 WS 타입 티켓 발급 결과를 반환
        UUID ticket = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Instant expiresAt = Instant.parse("2026-07-26T00:01:00Z");
        given(connectionService.token(eq(1L), any(ConnectionTokenCommand.class)))
                .willReturn(new ConnectionTokenResult(ticket, ConnectionType.WS, expiresAt));

        // when: 인증 상태로 WS 연결 토큰 발급 API 호출
        var result = mockMvc.perform(post("/api/v1/connection-tokens")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectionType\":\"ws\"}"));

        // then: 항상 새 티켓을 만드는 호출이므로 201 + 서비스 결과가 응답 JSON으로 매핑 + 인증 유저 id·커맨드로 서비스에 위임
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticket").value(ticket.toString()))
                .andExpect(jsonPath("$.connectionType").value("ws"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()));
        then(connectionService).should().token(1L, new ConnectionTokenCommand(ConnectionType.WS));
    }

    @Test
    @DisplayName("connectionType이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void token_without_connectionType_returns_400() throws Exception {
        // when: connectionType이 빠진 본문으로 연결 토큰 발급 API 호출
        var result = mockMvc.perform(post("/api/v1/connection-tokens")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: @NotNull 검증 실패로 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(connectionService).should(never()).token(anyLong(), any());
    }

    @Test
    @DisplayName("connectionType이 허용되지 않는 값이면 400을 반환하고 서비스를 호출하지 않는다")
    void token_with_unknown_connectionType_returns_400() throws Exception {
        // when: 정의되지 않은 connectionType 값으로 연결 토큰 발급 API 호출
        var result = mockMvc.perform(post("/api/v1/connection-tokens")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectionType\":\"http\"}"));

        // then: 본문 역직렬화 실패로 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(connectionService).should(never()).token(anyLong(), any());
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void token_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 연결 토큰 발급 API 호출
        var result = mockMvc.perform(post("/api/v1/connection-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectionType\":\"ws\"}"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(connectionService).should(never()).token(anyLong(), any());
    }
}

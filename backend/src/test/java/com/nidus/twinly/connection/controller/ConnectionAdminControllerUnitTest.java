package com.nidus.twinly.connection.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.domain.ConnectionDrainingScope;
import com.nidus.twinly.connection.dto.command.ConnectionDrainingCommand;
import com.nidus.twinly.connection.service.ConnectionService;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConnectionAdminController.class)
@Import(SecurityConfig.class)
class ConnectionAdminControllerUnitTest {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConnectionService connectionService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("draining 예고 성공 시 200을 반환하고 사유·대기시간·전파범위로 서비스를 호출한다")
    void notifyDraining_success() throws Exception {
        // when: 관리자 토큰으로 배포 사유의 draining 예고 요청
        var result = mockMvc.perform(post("/admin/connection/draining")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "deploy", "retryAfterMs": 3000, "scope": "all"}
                        """));

        // then: 200 반환 + 요청 본문 값이 그대로 커맨드에 담겨 서비스로 전달됨
        result.andExpect(status().isOk());

        ArgumentCaptor<ConnectionDrainingCommand> captor = ArgumentCaptor.forClass(ConnectionDrainingCommand.class);
        then(connectionService).should().notifyDraining(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo(ConnectionDrainingReason.DEPLOY);
        assertThat(captor.getValue().retryAfterMs()).isEqualTo(3000L);
        assertThat(captor.getValue().scope()).isEqualTo(ConnectionDrainingScope.ALL);
    }

    @Test
    @DisplayName("draining 예고 시 사유가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void notifyDraining_withoutReason_returns_400() throws Exception {
        // when: 필수 필드 reason을 뺀 요청 (클라이언트가 행동을 결정할 근거가 없다)
        var result = mockMvc.perform(post("/admin/connection/draining")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"scope": "all"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(connectionService).should(never()).notifyDraining(any());
    }

    @Test
    @DisplayName("draining 예고 시 전파 범위가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void notifyDraining_withoutScope_returns_400() throws Exception {
        // when: 필수 필드 scope를 뺀 요청 (한 대만 비울지 전체에 뿌릴지는 운영자가 매번 정해야 한다)
        var result = mockMvc.perform(post("/admin/connection/draining")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "deploy"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(connectionService).should(never()).notifyDraining(any());
    }

    @Test
    @DisplayName("draining 예고 시 retryAfterMs가 양수가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void notifyDraining_withNonPositiveRetryAfter_returns_400() throws Exception {
        // when: retryAfterMs를 0으로 보낸 요청 (0 이하가 통과하면 클라이언트가 즉시 재연결해 재연결 폭풍이 난다)
        var result = mockMvc.perform(post("/admin/connection/draining")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "deploy", "retryAfterMs": 0, "scope": "all"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(connectionService).should(never()).notifyDraining(any());
    }

    @Test
    @DisplayName("draining 예고 시 정의되지 않은 사유는 400을 반환하고 서비스를 호출하지 않는다")
    void notifyDraining_withUnknownReason_returns_400() throws Exception {
        // when: 열거형에 없는 사유로 요청 (클라이언트가 해석할 수 없는 사유는 파싱 단계에서 걸러야 한다)
        var result = mockMvc.perform(post("/admin/connection/draining")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "unknown", "scope": "all"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(connectionService).should(never()).notifyDraining(any());
    }

    @Test
    @DisplayName("관리자 토큰이 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void notifyDraining_withoutAdminToken_returns_401() throws Exception {
        // when: 관리자 토큰 없이 draining 예고 요청
        var result = mockMvc.perform(post("/admin/connection/draining")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "deploy", "scope": "all"}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(connectionService).should(never()).notifyDraining(any());
    }
}

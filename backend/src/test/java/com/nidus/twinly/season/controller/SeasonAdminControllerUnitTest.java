package com.nidus.twinly.season.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.purchase.domain.PoolAssignment;
import com.nidus.twinly.season.dto.result.SeasonChangeResult;
import com.nidus.twinly.season.dto.result.SeasonPoolReassignResult;
import com.nidus.twinly.season.dto.result.SeasonReservationResult;
import com.nidus.twinly.season.service.SeasonReservationService;
import com.nidus.twinly.season.service.SeasonService;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeasonAdminController.class)
@Import(SecurityConfig.class)
class SeasonAdminControllerUnitTest {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SeasonService seasonService;

    @MockitoBean
    SeasonReservationService seasonReservationService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("시즌 전환 예약 성공 시 200과 예약 정보를 반환한다")
    void reserveSeason_success() throws Exception {
        // given
        given(seasonReservationService.reserve(any(), any())).willReturn(new SeasonReservationResult(
                3L, Instant.parse("2026-10-31T15:00:00Z"), Instant.parse("2026-11-30T15:00:00Z")));

        // when
        var result = mockMvc.perform(post("/admin/season/reservation")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt": "2026-10-31T15:00:00Z", "endedAt": "2026-11-30T15:00:00Z"}
                        """));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonId").value("3"));
    }

    @Test
    @DisplayName("시즌 전환 예약 API 는 관리자 토큰 없이 호출할 수 없다")
    void reservation_requiresAdminToken() throws Exception {
        mockMvc.perform(delete("/admin/season/reservation"))
                .andExpect(status().isUnauthorized());

        then(seasonReservationService).should(never()).cancel();
    }

    @Test
    @DisplayName("풀 재배정 성공 시 200과 풀별 인원을 반환한다")
    void reassignPools_success() throws Exception {
        // given: 서비스가 재배정 결과를 반환
        given(seasonService.reassignPools()).willReturn(SeasonPoolReassignResult.from(List.of(
                new PoolAssignment(13, 80, 42, 38), new PoolAssignment(14, 5, 3, 2))));

        // when: 관리자 토큰으로 재배정 요청
        var result = mockMvc.perform(post("/admin/season/pools/reassign").header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 전체 인원과 풀별 성비가 내려간다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCount").value(85))
                .andExpect(jsonPath("$.pools[0].poolNumber").value(13))
                .andExpect(jsonPath("$.pools[0].maleCount").value(42))
                .andExpect(jsonPath("$.pools[1].memberCount").value(5));
    }

    @Test
    @DisplayName("풀 재배정은 관리자 토큰 없이 호출하면 401이고 서비스를 부르지 않는다")
    void reassignPools_requiresAdminToken() throws Exception {
        mockMvc.perform(post("/admin/season/pools/reassign"))
                .andExpect(status().isUnauthorized());

        then(seasonService).should(never()).reassignPools();
    }

    @Test
    @DisplayName("시즌 전환 성공 시 200과 생성된 시즌 정보를 반환하고 요청 기간으로 서비스를 호출한다")
    void changeSeason_success() throws Exception {
        // given: 서비스가 새 시즌 생성 결과를 반환
        given(seasonService.changeSeason(any())).willReturn(new SeasonChangeResult(
                7L, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z")));

        // when: 관리자 토큰으로 시즌 전환 요청
        var result = mockMvc.perform(post("/admin/season")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt": "2026-01-01T00:00:00Z", "endedAt": "2026-02-01T00:00:00Z"}
                        """));

        // then: 200 반환 + seasonId는 문자열로 내려가고, 요청 기간이 커맨드에 담김
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonId").value("7"));

        ArgumentCaptor<SeasonChangeCommand> captor = ArgumentCaptor.forClass(SeasonChangeCommand.class);
        then(seasonService).should().changeSeason(captor.capture());
        assertThat(captor.getValue().startedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(captor.getValue().endedAt()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    @DisplayName("시즌 전환 시 종료 시각이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void changeSeason_withoutEndedAt_returns_400() throws Exception {
        // when: 필수 필드 endedAt을 뺀 요청
        var result = mockMvc.perform(post("/admin/season")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt": "2026-01-01T00:00:00Z"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(seasonService).should(never()).changeSeason(any());
    }

    @Test
    @DisplayName("시작이 종료보다 뒤면 서비스의 INVALID_SEASON_PERIOD 예외가 422로 매핑된다")
    void changeSeason_invalidPeriod_returns_422() throws Exception {
        // given: 서비스가 기간 역전 도메인 예외를 던지도록 설정
        given(seasonService.changeSeason(any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_SEASON_PERIOD));

        // when: 시작이 종료보다 뒤인 기간으로 시즌 전환 요청
        var result = mockMvc.perform(post("/admin/season")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt": "2026-02-01T00:00:00Z", "endedAt": "2026-01-01T00:00:00Z"}
                        """));

        // then: 도메인 예외가 422와 에러 코드로 매핑됨
        result.andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_SEASON_PERIOD.name()));
    }

    @Test
    @DisplayName("관리자 토큰이 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void changeSeason_withoutAdminToken_returns_401() throws Exception {
        // when: 관리자 토큰 없이 시즌 전환 요청
        var result = mockMvc.perform(post("/admin/season")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startedAt": "2026-01-01T00:00:00Z", "endedAt": "2026-02-01T00:00:00Z"}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(seasonService).should(never()).changeSeason(any());
    }
}

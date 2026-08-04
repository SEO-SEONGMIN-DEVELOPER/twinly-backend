package com.nidus.twinly.season.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.service.SeasonService;
import com.nidus.twinly.user.dto.header.UserInfo;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeasonController.class)
@Import(SecurityConfig.class)
class SeasonControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SeasonService seasonService;

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
    @DisplayName("시즌 참가 성공 시 200을 반환하고 인증 유저 id로 서비스를 호출한다")
    void participateIn_success() throws Exception {
        // when: 인증 상태로 시즌 참가 API 호출
        var result = mockMvc.perform(put("/api/v1/season/participation")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 인증 유저 id로 서비스에 위임
        result.andExpect(status().isOk());
        then(seasonService).should().participateIn(1L);
    }

    @Test
    @DisplayName("시즌 참가 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void participateIn_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 시즌 참가 API 호출
        var result = mockMvc.perform(put("/api/v1/season/participation"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
        then(seasonService).should(never()).participateIn(anyLong());
    }

    @Test
    @DisplayName("참가 기간이 아니어서 서비스가 SEASON_NOT_JOINABLE을 던지면 422와 에러 코드를 반환한다")
    void participateIn_when_season_not_joinable_returns_422() throws Exception {
        // given: 서비스가 참가 불가 도메인 예외를 던짐
        willThrow(new BusinessException(ErrorCode.SEASON_NOT_JOINABLE))
                .given(seasonService).participateIn(1L);

        // when: 인증 상태로 시즌 참가 API 호출
        var result = mockMvc.perform(put("/api/v1/season/participation")
                .header("Authorization", "Bearer access-token"));

        // then: 도메인 예외가 422 + SEASON_NOT_JOINABLE 코드로 매핑됨
        result.andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value(ErrorCode.SEASON_NOT_JOINABLE.name()));
    }

    @Test
    @DisplayName("시즌 참가 조회 시 서비스 결과를 응답 JSON으로 변환하고 currentSeasonId는 문자열로 직렬화한다")
    void participation_success() throws Exception {
        // given: 서비스가 참가 이력이 있는 결과를 반환
        given(seasonService.participation(1L))
                .willReturn(new SeasonParticipationResult(7L, Instant.parse("2026-07-01T00:00:00Z")));

        // when: 인증 상태로 시즌 참가 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/participation")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + currentSeasonId 문자열 직렬화 + participatedInAt ISO-8601 직렬화
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeasonId").value("7"))
                .andExpect(jsonPath("$.participatedInAt").value("2026-07-01T00:00:00Z"));
        then(seasonService).should().participation(1L);
    }

    @Test
    @DisplayName("시즌 참가 이력이 없으면 participatedInAt을 null로 응답한다")
    void participation_when_not_participated_returns_null_participatedInAt() throws Exception {
        // given: 서비스가 참가 이력 없는 결과를 반환
        given(seasonService.participation(1L))
                .willReturn(new SeasonParticipationResult(7L, null));

        // when: 인증 상태로 시즌 참가 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/participation")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + participatedInAt은 null
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSeasonId").value("7"))
                .andExpect(jsonPath("$.participatedInAt", nullValue()));
    }

    @Test
    @DisplayName("시즌 참가 조회 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void participation_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 시즌 참가 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/participation"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
        then(seasonService).should(never()).participation(anyLong());
    }
}

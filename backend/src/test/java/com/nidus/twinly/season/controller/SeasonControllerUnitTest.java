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
    @DisplayName("시즌 참여 시 인증된 유저 id로 서비스에 위임하고 본문 없이 200을 반환한다")
    void participateIn_success() throws Exception {
        // when: 인증 상태로 시즌 참여 API 호출
        var result = mockMvc.perform(put("/api/v1/season/participation")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 인증에서 뽑은 유저 id로 위임
        result.andExpect(status().isOk());
        then(seasonService).should().participateIn(1L);
    }

    @Test
    @DisplayName("시즌 참여 시 결제 권한이 없으면 403과 SIMULATION_ACCESS_REQUIRED를 반환한다")
    void participateIn_without_access_returns_403() throws Exception {
        // given: 서비스가 권한 없음으로 거절
        willThrow(new BusinessException(ErrorCode.SIMULATION_ACCESS_REQUIRED))
                .given(seasonService).participateIn(1L);

        // when: 인증 상태로 시즌 참여 API 호출
        var result = mockMvc.perform(put("/api/v1/season/participation")
                .header("Authorization", "Bearer access-token"));

        // then: 403 반환 + 권한 부족임을 코드로 구분 (약관 미동의와 섞이면 앱이 다른 화면을 띄운다)
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.SIMULATION_ACCESS_REQUIRED.name()));
    }

    @Test
    @DisplayName("시즌 참여 시 필수 약관에 동의하지 않았으면 403과 SIMULATION_CONSENT_REQUIRED를 반환한다")
    void participateIn_without_consent_returns_403() throws Exception {
        // given: 서비스가 약관 미동의로 거절
        willThrow(new BusinessException(ErrorCode.SIMULATION_CONSENT_REQUIRED))
                .given(seasonService).participateIn(1L);

        // when: 인증 상태로 시즌 참여 API 호출
        var result = mockMvc.perform(put("/api/v1/season/participation")
                .header("Authorization", "Bearer access-token"));

        // then: 403 반환 + 약관 미동의임을 코드로 구분
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.SIMULATION_CONSENT_REQUIRED.name()));
    }

    @Test
    @DisplayName("시즌 참여 시 유저가 없으면 404와 USER_NOT_FOUND를 반환한다")
    void participateIn_when_user_missing_returns_404() throws Exception {
        // given: 토큰은 유효하지만 유저가 사라진 상태
        willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
                .given(seasonService).participateIn(1L);

        // when: 인증 상태로 시즌 참여 API 호출
        var result = mockMvc.perform(put("/api/v1/season/participation")
                .header("Authorization", "Bearer access-token"));

        // then: 404 반환
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.name()));
    }

    @Test
    @DisplayName("시즌 참여 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void participateIn_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 시즌 참여 API 호출
        var result = mockMvc.perform(put("/api/v1/season/participation"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
        then(seasonService).should(never()).participateIn(anyLong());
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

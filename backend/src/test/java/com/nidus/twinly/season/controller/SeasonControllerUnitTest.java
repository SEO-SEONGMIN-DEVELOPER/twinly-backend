package com.nidus.twinly.season.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.domain.SeasonRecapTimeSlot;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityTypeCountsResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapActivityTypeResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapBestMatchResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapCountResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapMinutesResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapMostMetResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapPlaceResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapResult;
import com.nidus.twinly.season.dto.result.SeasonLatestRecapTogetherResult;
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
import java.util.List;

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

    @Test
    @DisplayName("최근 리캡 조회 성공 시 200과 함께 id는 문자열로, 0인 항목의 topPercent와 없는 활동 유형은 null로 응답한다")
    void latestRecap_success() throws Exception {
        // given: 서비스가 7번 시즌 리캡을 반환 (이동 0분, 활동 유형 없음)
        given(seasonService.latestRecap(1L)).willReturn(new SeasonLatestRecapResult(
                7L,
                Instant.parse("2026-09-30T15:00:00Z"),
                Instant.parse("2026-10-31T14:59:59Z"),
                812,
                new SeasonLatestRecapTogetherResult(24),
                new SeasonLatestRecapActivityResult(
                        new SeasonLatestRecapMinutesResult(2460, 12),
                        new SeasonLatestRecapMinutesResult(9120, 40),
                        new SeasonLatestRecapMinutesResult(0, null)),
                new SeasonLatestRecapCountResult(1284, 8),
                List.of(new SeasonLatestRecapPlaceResult("P0123", "학생 거리 카페", 18, 1260)),
                new SeasonLatestRecapCountResult(27, 30),
                new SeasonLatestRecapCountResult(6, 21),
                List.of(new SeasonLatestRecapMostMetResult(123L, "닉네임", null, 12, 40)),
                List.of(),
                List.of(new SeasonLatestRecapBestMatchResult(456L, "닉네임2", null, 90.0, 10, 9)),
                new SeasonLatestRecapCountResult(19, 15),
                new SeasonLatestRecapActivityTypeResult(SeasonRecapTimeSlot.LATE_NIGHT,
                        new SeasonLatestRecapActivityTypeCountsResult(14, 23, 54, 60))
        ));

        // when: 인증 상태로 최근 리캡 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/recaps/latest")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 명세 모양 그대로 직렬화
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonId").value("7"))
                .andExpect(jsonPath("$.population").value(812))
                .andExpect(jsonPath("$.together.days").value(24))
                .andExpect(jsonPath("$.activity.dialogue.minutes").value(2460))
                .andExpect(jsonPath("$.activity.move.topPercent").value(nullValue()))
                .andExpect(jsonPath("$.places[0].placeCode").value("P0123"))
                .andExpect(jsonPath("$.mostMet[0].userId").value("123"))
                .andExpect(jsonPath("$.mostMet[0].profilePhoto").value(nullValue()))
                .andExpect(jsonPath("$.closest").isEmpty())
                .andExpect(jsonPath("$.bestMatches[0].userId").value("456"))
                .andExpect(jsonPath("$.bestMatches[0].matchRate").value(90.0))
                .andExpect(jsonPath("$.activityType.type").value("lateNight"))
                .andExpect(jsonPath("$.activityType.counts.lateNight").value(60));

        // then: 인증 유저 id로 위임
        then(seasonService).should().latestRecap(1L);
    }

    @Test
    @DisplayName("제공 중인 리캡이 없으면 404와 SEASON_RECAP_NOT_FOUND를 반환한다")
    void latestRecap_not_found_returns_404() throws Exception {
        // given: 서비스가 리캡 없음으로 거절
        given(seasonService.latestRecap(1L)).willThrow(new BusinessException(ErrorCode.SEASON_RECAP_NOT_FOUND));

        // when: 인증 상태로 최근 리캡 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/recaps/latest")
                .header("Authorization", "Bearer access-token"));

        // then: 404 + 오류 코드
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_RECAP_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 최근 리캡 조회는 401을 반환하고 서비스를 호출하지 않는다")
    void latestRecap_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 최근 리캡 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/season/recaps/latest"));

        // then: 401 + 서비스 미호출
        result.andExpect(status().isUnauthorized());
        then(seasonService).should(never()).latestRecap(anyLong());
    }
}

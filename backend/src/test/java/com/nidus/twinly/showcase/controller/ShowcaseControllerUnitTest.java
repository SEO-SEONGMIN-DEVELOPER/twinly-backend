package com.nidus.twinly.showcase.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.showcase.dto.result.ShowcaseActionSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseBubbleLineResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseDialogueSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseMoveSceneResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseTodayResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseTodaySectorsResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseUserCountsResult;
import com.nidus.twinly.showcase.dto.result.ShowcaseUserInfoResult;
import com.nidus.twinly.showcase.service.ShowcaseService;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShowcaseController.class)
@Import(SecurityConfig.class)
class ShowcaseControllerUnitTest {

    private static final String SECTOR_FROM = "2026-10-16T13:00:00+09:00";
    private static final String SECTOR_TO = "2026-10-16T14:00:00+09:00";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ShowcaseService showcaseService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString()))
                .willReturn(new UserInfo(12L));
    }

    @Test
    @DisplayName("관람 조회 성공 시 200과 userRef·장면·인물·사용자 수를 반환한다")
    void today_success() throws Exception {
        // given: 서비스가 오늘 배정된 관람 결과를 돌려준다
        given(showcaseService.today(12L)).willReturn(todayResult());

        // when: 인증 상태로 관람 API 호출
        var result = mockMvc.perform(get("/api/v1/showcases/today")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 숫자 id·ref가 문자열로 직렬화되고 실제 유저 id는 나가지 않는다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.showcaseId").value("3312"))
                .andExpect(jsonPath("$.userRef").value("1"))
                .andExpect(jsonPath("$.date").value("2026-08-18"))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].sceneId").value("88101"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("SCHOOL_GATE"))
                .andExpect(jsonPath("$.scenes[1].placeCode").value(nullValue()))
                .andExpect(jsonPath("$.scenes[1].type").value("dialogue"))
                .andExpect(jsonPath("$.scenes[1].with[0]").value("2"))
                .andExpect(jsonPath("$.scenes[1].lines[0].t").value("bubble"))
                .andExpect(jsonPath("$.scenes[1].lines[0].userRef").value("2"))
                .andExpect(jsonPath("$.userInfos[0].userName").value("김OO"))
                .andExpect(jsonPath("$.userInfos[0].gender").value("male"))
                .andExpect(jsonPath("$.userInfos[0].organization").value("한국대"))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto").doesNotExist())
                .andExpect(jsonPath("$.userCounts.total").value(12840))
                .andExpect(jsonPath("$.userCounts.sameOrganization").value(320))
                .andExpect(jsonPath("$.userCounts.organization").value("성신여대"));
        then(showcaseService).should().today(12L);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void today_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 관람 API 호출
        var result = mockMvc.perform(get("/api/v1/showcases/today"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(showcaseService).should(never()).today(anyLong());
    }

    @Test
    @DisplayName("v1 관람 조회는 move 장면을 도착지 기준 action 장면으로 바꿔 내려 구버전 앱 디코더를 통과시킨다")
    void today_v1_converts_move_scene_to_action() throws Exception {
        // given: 서비스가 관람 대상이 다른 인물(ref 2)과 함께 이동한 move 장면을 돌려준다
        given(showcaseService.today(12L)).willReturn(moveResult());

        // when: 출시된 앱이 쓰는 v1 관람 API 호출
        var result = mockMvc.perform(get("/api/v1/showcases/today")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + action/dialogue 외 타입이 없고, with 는 userRef 그대로이며 move 전용 필드는 없다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].place").value("학생 거리 탐앤탐스"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("P0158"))
                .andExpect(jsonPath("$.scenes[0].with[0]").value("2"))
                .andExpect(jsonPath("$.scenes[0].narration").value("김OO과 카페로 향했다"))
                .andExpect(jsonPath("$.scenes[0].fromPlace").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].travelMode").doesNotExist());
    }

    @Test
    @DisplayName("v2 관람 조회는 move 장면을 출발지·도착지·이동 수단과 함께 내리고 with 는 userRef 목록이다")
    void today_v2_returns_move_scene() throws Exception {
        // given: 서비스가 관람 대상이 다른 인물(ref 2)과 함께 이동한 move 장면을 돌려준다
        given(showcaseService.today(12L)).willReturn(moveResult());

        // when: 새 앱이 쓰는 v2 관람 API 호출
        var result = mockMvc.perform(get("/api/v2/showcases/today")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + move 모양으로 내려가고 나머지 필드는 v1 과 같다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.showcaseId").value("3312"))
                .andExpect(jsonPath("$.userRef").value("1"))
                .andExpect(jsonPath("$.scenes[0].sceneId").value("88103"))
                .andExpect(jsonPath("$.scenes[0].type").value("move"))
                .andExpect(jsonPath("$.scenes[0].fromPlace").value("집"))
                .andExpect(jsonPath("$.scenes[0].fromPlaceCode").value("P0258"))
                .andExpect(jsonPath("$.scenes[0].place").value("학생 거리 탐앤탐스"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("P0158"))
                .andExpect(jsonPath("$.scenes[0].with[0]").value("2"))
                .andExpect(jsonPath("$.scenes[0].travelMode").value("transit"))
                .andExpect(jsonPath("$.scenes[0].mapVersion").value("sha256:89af"))
                .andExpect(jsonPath("$.userInfos[0].userName").value("김OO"))
                .andExpect(jsonPath("$.userCounts.total").value(12840));
        then(showcaseService).should().today(12L);
    }

    @Test
    @DisplayName("v2 관람 조회에 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void today_v2_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 v2 관람 API 호출
        var result = mockMvc.perform(get("/api/v2/showcases/today"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(showcaseService).should(never()).today(anyLong());
    }

    @Test
    @DisplayName("v2 관람 조회에서 서비스가 SHOWCASE_TARGET_NOT_FOUND를 던지면 404를 반환한다")
    void today_v2_target_not_found_returns_404() throws Exception {
        // given: 오늘 장면이 있는 관람 대상 후보가 없음
        given(showcaseService.today(12L)).willThrow(new BusinessException(ErrorCode.SHOWCASE_TARGET_NOT_FOUND));

        // when: v2 관람 API 호출
        var result = mockMvc.perform(get("/api/v2/showcases/today")
                .header("Authorization", "Bearer access-token"));

        // then: 404 SHOWCASE_TARGET_NOT_FOUND 반환
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOWCASE_TARGET_NOT_FOUND"));
    }

    @Test
    @DisplayName("건물 화면 조회 성공 시 200 과 함께 showcaseId 를 문자열로, refVersion 과 focusRef user:1 을 내려 주고 경로·쿼리를 그대로 넘긴다")
    void todaySectors_success() throws Exception {
        // given: 서비스가 대상(user:1) 한 명이 든 1시간 창을 돌려준다
        ObjectNode actor = new ObjectMapper().createObjectNode().put("ref", "user:1").put("displayName", "김OO");
        given(showcaseService.todaySectors(12L, "S034", LocalDate.parse("2026-10-16"), SECTOR_FROM, SECTOR_TO))
                .willReturn(new ShowcaseTodaySectorsResult("7", LocalDate.parse("2026-10-16"), "S034", "sha256:aa",
                        OffsetDateTime.parse("2026-10-16T13:20:00+09:00"), "user:1", 3312L, 2,
                        List.of(actor), List.of(), List.of(), List.of(), List.of(), List.of()));

        // when: 인증 상태로 둘러보기 건물 화면 조회
        var result = mockMvc.perform(get("/api/v1/showcases/today/sectors/{sectorCode}", "S034")
                .header("Authorization", "Bearer access-token")
                .param("date", "2026-10-16").param("from", SECTOR_FROM).param("to", SECTOR_TO));

        // then: 200 + showcaseId 문자열 + refVersion + 원소 그대로
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.showcaseId").value("3312"))
                .andExpect(jsonPath("$.refVersion").value(2))
                .andExpect(jsonPath("$.focusRef").value("user:1"))
                .andExpect(jsonPath("$.actors[0].displayName").value("김OO"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("건물 화면: 인증 헤더가 없으면 401, from·to 가 없거나 구역 코드 형식이 틀리면 400 이고 서비스를 호출하지 않는다")
    void todaySectors_rejects_unauthenticated_and_invalid_requests() throws Exception {
        // when & then: 인증 헤더 없음
        mockMvc.perform(get("/api/v1/showcases/today/sectors/{sectorCode}", "S034")
                        .param("date", "2026-10-16").param("from", SECTOR_FROM).param("to", SECTOR_TO))
                .andExpect(status().isUnauthorized());

        // when & then: from·to 없음
        mockMvc.perform(get("/api/v1/showcases/today/sectors/{sectorCode}", "S034").header("Authorization", "Bearer access-token")
                        .param("date", "2026-10-16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));

        // when & then: 구역 코드 형식 틀림
        mockMvc.perform(get("/api/v1/showcases/today/sectors/{sectorCode}", "S03").header("Authorization", "Bearer access-token")
                        .param("date", "2026-10-16").param("from", SECTOR_FROM).param("to", SECTOR_TO))
                .andExpect(status().isBadRequest());

        // then: 서비스는 호출되지 않음
        then(showcaseService).should(never()).todaySectors(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("건물 화면: 트랙이 없으면 404 WORLD_TRACK_NOT_FOUND 로 매핑된다")
    void todaySectors_maps_not_found() throws Exception {
        // given: 서비스가 대상의 그날 트랙이 없다고 예외를 던진다
        given(showcaseService.todaySectors(12L, "S034", LocalDate.parse("2026-10-16"), SECTOR_FROM, SECTOR_TO))
                .willThrow(new BusinessException(ErrorCode.WORLD_TRACK_NOT_FOUND));

        // when: 건물 화면 조회
        var result = mockMvc.perform(get("/api/v1/showcases/today/sectors/{sectorCode}", "S034")
                .header("Authorization", "Bearer access-token")
                .param("date", "2026-10-16").param("from", SECTOR_FROM).param("to", SECTOR_TO));

        // then: 404 와 WORLD_TRACK_NOT_FOUND 코드
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORLD_TRACK_NOT_FOUND.name()));
    }

    private ShowcaseTodayResult moveResult() {
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-08-18T11:00:00+09:00");
        OffsetDateTime endsAt = OffsetDateTime.parse("2026-08-18T11:20:00+09:00");

        return new ShowcaseTodayResult(
                3312L,
                0,
                1L,
                LocalDate.parse("2026-08-18"),
                Instant.parse("2026-08-18T04:20:11Z"),
                List.of(new ShowcaseMoveSceneResult(88103L, "move", startsAt, endsAt, "집", "P0258", "학생 거리 탐앤탐스", "P0158",
                        List.of(2L), "transit", "sha256:89af", "김OO과 카페로 향했다", null)),
                List.of(new ShowcaseUserInfoResult(1L, "김OO", Gender.MALE, "한국대"),
                        new ShowcaseUserInfoResult(2L, "박OO", Gender.FEMALE, "한국대")),
                new ShowcaseUserCountsResult(12840, 320, "성신여대")
        );
    }

    private ShowcaseTodayResult todayResult() {
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-08-18T09:00:00+09:00");
        OffsetDateTime endsAt = OffsetDateTime.parse("2026-08-18T09:40:00+09:00");

        return new ShowcaseTodayResult(
                3312L,
                0,
                1L,
                LocalDate.parse("2026-08-18"),
                Instant.parse("2026-08-18T04:20:11Z"),
                List.of(
                        new ShowcaseActionSceneResult(88101L, "action", startsAt, endsAt, "학교 정문", "SCHOOL_GATE", List.of(), "김OO이 뛰었다.", "아슬아슬했다.", null),
                        new ShowcaseDialogueSceneResult(88102L, "dialogue", startsAt, endsAt, "식당", null, List.of(2L),
                                List.of(new ShowcaseBubbleLineResult("bubble", 2L, "웃으며", "여기 앉아.", startsAt)))
                ),
                List.of(new ShowcaseUserInfoResult(1L, "김OO", Gender.MALE, "한국대")),
                new ShowcaseUserCountsResult(12840, 320, "성신여대")
        );
    }
}

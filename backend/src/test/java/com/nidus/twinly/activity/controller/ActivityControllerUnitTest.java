package com.nidus.twinly.activity.controller;

import com.nidus.twinly.activity.dto.result.ActivityActionSceneResult;
import com.nidus.twinly.common.scene.SceneBubbleLine;
import com.nidus.twinly.common.scene.SceneNarrationLine;
import com.nidus.twinly.activity.dto.result.ActivityDialogueSceneResult;
import com.nidus.twinly.activity.dto.result.ActivityMoveSceneResult;
import com.nidus.twinly.activity.dto.result.ActivityQuestionResult;
import com.nidus.twinly.activity.dto.result.ActivityResult;
import com.nidus.twinly.activity.dto.result.ActivityUserInfoResult;
import com.nidus.twinly.activity.service.ActivityService;
import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
@Import(SecurityConfig.class)
class ActivityControllerUnitTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 26);
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ActivityService activityService;

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
    @DisplayName("활동 조회 성공 시 200과 함께 서비스 결과를 응답 JSON으로 변환하고 id들은 문자열로 직렬화한다")
    void activity_success() throws Exception {
        // given: 서비스가 action 씬 1개, dialogue 씬 1개, 질문 1개를 반환
        given(activityService.activity(1L, DATE)).willReturn(sampleResult());

        // when: 인증 상태로 특정 날짜의 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/activities/{date}", "2026-07-26")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + userId/seasonId/sceneId/질문 id가 문자열로 직렬화된 JSON 응답
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("1"))
                .andExpect(jsonPath("$.seasonId").value("7"))
                .andExpect(jsonPath("$.date").value("2026-07-26"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.serverNow").isNotEmpty())
                .andExpect(jsonPath("$.scenes", hasSize(2)))
                .andExpect(jsonPath("$.scenes[0].sceneId").value("10"))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].startsAt", startsWith("2026-07-26T09:00")))
                .andExpect(jsonPath("$.scenes[0].endsAt", startsWith("2026-07-26T10:00")))
                .andExpect(jsonPath("$.scenes[0].place").value("학교 복도"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("SCHOOL_HALLWAY"))
                .andExpect(jsonPath("$.scenes[0].narration").value("복도를 천천히 걸었다"))
                .andExpect(jsonPath("$.scenes[0].mind").value("조금 설레었다"))
                .andExpect(jsonPath("$.scenes[0].with", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].with[0]", is("100")))
                .andExpect(jsonPath("$.scenes[1].sceneId").value("11"))
                .andExpect(jsonPath("$.scenes[1].type").value("dialogue"))
                .andExpect(jsonPath("$.scenes[1].lines", hasSize(2)))
                .andExpect(jsonPath("$.scenes[1].lines[0].t").value("narr"))
                .andExpect(jsonPath("$.scenes[1].lines[0].text").value("교실이 조용해졌다"))
                .andExpect(jsonPath("$.scenes[1].lines[0].occursAt", startsWith("2026-07-26T12:00")))
                .andExpect(jsonPath("$.scenes[1].lines[1].t").value("bubble"))
                .andExpect(jsonPath("$.scenes[1].lines[1].userId", is("100")))
                .andExpect(jsonPath("$.scenes[1].lines[1].action").value("웃으며"))
                .andExpect(jsonPath("$.scenes[1].lines[1].text").value("안녕"))
                .andExpect(jsonPath("$.scenes[1].lines[1].occursAt", startsWith("2026-07-26T12:05")))
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].id").value("50"))
                .andExpect(jsonPath("$.questions[0].type").value("PROMISE"))
                .andExpect(jsonPath("$.questions[0].time", startsWith("2026-07-26T21:30")))
                .andExpect(jsonPath("$.questions[0].text").value("오늘 어땠어?"))
                .andExpect(jsonPath("$.questions[0].options", hasSize(2)))
                .andExpect(jsonPath("$.questions[0].options[0]").value("좋았어"))
                .andExpect(jsonPath("$.userInfos", hasSize(1)))
                .andExpect(jsonPath("$.userInfos[0].userId").value("100"))
                .andExpect(jsonPath("$.userInfos[0].userName").value("홍길동"))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto.key").value("profile/100/key"))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto.photoUrl").value("https://cdn.example.com/signed"))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto.position.startPos.x").value(10))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto.position.height").value(200));

        // then: 인증 유저 id(1)와 경로에서 LocalDate로 변환된 date로 서비스에 위임
        then(activityService).should().activity(1L, DATE);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void activity_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/activities/{date}", "2026-07-26"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(activityService).should(never()).activity(anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("경로 변수 date가 날짜 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void activity_with_invalid_date_returns_400() throws Exception {
        // when: 경로 변수 date를 날짜로 변환할 수 없는 값으로 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/activities/{date}", "invalid-date")
                .header("Authorization", "Bearer access-token"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(activityService).should(never()).activity(anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("v1 활동 조회는 move 씬을 도착지 기준 action 씬으로 바꿔 내리고 출발지·이동 정보는 싣지 않는다")
    void activity_v1_converts_move_scene_to_action() throws Exception {
        // given: 서비스가 혼자 이동한 move 씬 1개를 반환
        given(activityService.activity(1L, DATE)).willReturn(moveResult());

        // when: 출시된 앱이 쓰는 v1 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/activities/{date}", "2026-07-26")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 기존 action 모양 그대로(장소는 도착지, 동행자 없으면 with 없음), move 전용 필드는 없다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].sceneId").value("12"))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].startsAt", startsWith("2026-07-26T11:00")))
                .andExpect(jsonPath("$.scenes[0].endsAt", startsWith("2026-07-26T11:10")))
                .andExpect(jsonPath("$.scenes[0].place").value("학생 거리 탐앤탐스"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("P0158"))
                .andExpect(jsonPath("$.scenes[0].narration").value("카페로 향했다"))
                .andExpect(jsonPath("$.scenes[0].with").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].fromPlace").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].travelMode").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].mapVersion").doesNotExist());
    }

    @Test
    @DisplayName("v2 활동 조회는 move 씬을 출발지·도착지·이동 수단·지도 버전과 함께 내리고 v1과 같은 서비스에 위임한다")
    void activity_v2_returns_move_scene() throws Exception {
        // given: 서비스가 혼자 이동한 move 씬 1개를 반환
        given(activityService.activity(1L, DATE)).willReturn(moveResult());

        // when: 새 앱이 쓰는 v2 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/activities/{date}", "2026-07-26")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + move 모양으로 내려가고, 동행자가 없으면 with 는 빈 배열이다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("1"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.scenes", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].sceneId").value("12"))
                .andExpect(jsonPath("$.scenes[0].type").value("move"))
                .andExpect(jsonPath("$.scenes[0].startsAt", startsWith("2026-07-26T11:00")))
                .andExpect(jsonPath("$.scenes[0].endsAt", startsWith("2026-07-26T11:10")))
                .andExpect(jsonPath("$.scenes[0].fromPlace").value("집"))
                .andExpect(jsonPath("$.scenes[0].fromPlaceCode").value("P0258"))
                .andExpect(jsonPath("$.scenes[0].place").value("학생 거리 탐앤탐스"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("P0158"))
                .andExpect(jsonPath("$.scenes[0].with", hasSize(0)))
                .andExpect(jsonPath("$.scenes[0].travelMode").value("walk"))
                .andExpect(jsonPath("$.scenes[0].mapVersion").value("sha256:89af"))
                .andExpect(jsonPath("$.scenes[0].narration").value("카페로 향했다"))
                .andExpect(jsonPath("$.userInfos", hasSize(1)));
        then(activityService).should().activity(1L, DATE);
    }

    @Test
    @DisplayName("v2 활동 조회는 action·dialogue 씬을 v1과 같은 모양으로 내린다")
    void activity_v2_keeps_action_and_dialogue_shape() throws Exception {
        // given: 서비스가 action 씬 1개, dialogue 씬 1개를 반환
        given(activityService.activity(1L, DATE)).willReturn(sampleResult());

        // when: v2 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/activities/{date}", "2026-07-26")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 두 씬 모두 v1 과 같은 필드로 내려간다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes", hasSize(2)))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].place").value("학교 복도"))
                .andExpect(jsonPath("$.scenes[0].with[0]", is("100")))
                .andExpect(jsonPath("$.scenes[1].type").value("dialogue"))
                .andExpect(jsonPath("$.scenes[1].lines", hasSize(2)))
                .andExpect(jsonPath("$.questions", hasSize(1)));
    }

    @Test
    @DisplayName("v2 활동 조회에 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void activity_v2_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 v2 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/activities/{date}", "2026-07-26"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(activityService).should(never()).activity(anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("v2 활동 조회에서 경로 변수 date가 날짜 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void activity_v2_with_invalid_date_returns_400() throws Exception {
        // when: 날짜로 변환할 수 없는 date 로 v2 활동 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/activities/{date}", "invalid-date")
                .header("Authorization", "Bearer access-token"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(activityService).should(never()).activity(anyLong(), any(LocalDate.class));
    }

    private ActivityResult moveResult() {
        ActivityMoveSceneResult move = new ActivityMoveSceneResult(
                12L,
                "move",
                OffsetDateTime.of(2026, 7, 26, 11, 0, 0, 0, KST),
                OffsetDateTime.of(2026, 7, 26, 11, 10, 0, 0, KST),
                "집",
                "P0258",
                "학생 거리 탐앤탐스",
                "P0158",
                List.of(),
                "walk",
                "sha256:89af",
                "카페로 향했다",
                null
        );

        ActivityUserInfoResult me = new ActivityUserInfoResult(1L, "자신", null);

        return new ActivityResult(1L, 7L, DATE, "v1", Instant.now(), List.of(move), List.of(), List.of(me));
    }

    private ActivityResult sampleResult() {
        Long userId = 100L;

        ActivityActionSceneResult action = new ActivityActionSceneResult(
                10L,
                "action",
                OffsetDateTime.of(2026, 7, 26, 9, 0, 0, 0, KST),
                OffsetDateTime.of(2026, 7, 26, 10, 0, 0, 0, KST),
                "학교 복도",
                "SCHOOL_HALLWAY",
                List.of(userId),
                "복도를 천천히 걸었다",
                "조금 설레었다"
        );

        ActivityDialogueSceneResult dialogue = new ActivityDialogueSceneResult(
                11L,
                "dialogue",
                OffsetDateTime.of(2026, 7, 26, 12, 0, 0, 0, KST),
                OffsetDateTime.of(2026, 7, 26, 12, 30, 0, 0, KST),
                "교실",
                null,
                List.of(userId),
                List.of(
                        new SceneNarrationLine("narr", "교실이 조용해졌다",
                                OffsetDateTime.of(2026, 7, 26, 12, 0, 0, 0, KST)),
                        new SceneBubbleLine("bubble", userId, "웃으며", "안녕",
                                OffsetDateTime.of(2026, 7, 26, 12, 5, 0, 0, KST))
                )
        );

        ActivityQuestionResult question = new ActivityQuestionResult(
                50L,
                "PROMISE",
                OffsetDateTime.of(2026, 7, 26, 21, 30, 0, 0, KST),
                "오늘 어땠어?",
                List.of("좋았어", "별로야")
        );

        ActivityUserInfoResult userInfo = new ActivityUserInfoResult(100L, "홍길동",
                new ProfilePhotoInfo("profile/100/key", "https://cdn.example.com/signed",
                        new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200)));

        return new ActivityResult(1L, 7L, DATE, "v1", Instant.now(), List.of(action, dialogue), List.of(question), List.of(userInfo));
    }
}

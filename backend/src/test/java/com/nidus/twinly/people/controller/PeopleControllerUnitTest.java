package com.nidus.twinly.people.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.people.dto.result.PeopleEventActionSceneResult;
import com.nidus.twinly.people.dto.result.PeopleEventMoveSceneResult;
import com.nidus.twinly.people.dto.result.PeopleEventResult;
import com.nidus.twinly.people.dto.result.PeopleEventUserInfoResult;
import com.nidus.twinly.people.dto.result.PeopleEventsItemResult;
import com.nidus.twinly.people.dto.result.PeopleEventsPageResult;
import com.nidus.twinly.people.dto.result.PeopleEventsPartnerResult;
import com.nidus.twinly.people.dto.result.PeopleEventsResult;
import com.nidus.twinly.people.dto.result.PeopleIntimacySeriesItemResult;
import com.nidus.twinly.people.dto.result.PeopleIntimacySeriesResult;
import com.nidus.twinly.people.dto.result.PeopleIntimacyQuizStatusResult;
import com.nidus.twinly.people.dto.result.PeopleItemResult;
import com.nidus.twinly.people.dto.result.PeopleLearnedFactsResult;
import com.nidus.twinly.people.dto.result.PeoplePageResult;
import com.nidus.twinly.people.dto.result.PeopleProfileDisclosedFieldsResult;
import com.nidus.twinly.people.dto.result.PeopleProfileResult;
import com.nidus.twinly.people.dto.result.PeopleProfileV2Result;
import com.nidus.twinly.people.dto.result.PeopleIntimacyQuizMatchRateResult;
import com.nidus.twinly.people.dto.result.PeopleResult;
import com.nidus.twinly.people.dto.result.PeopleThresholdResult;
import com.nidus.twinly.people.service.PeopleService;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PeopleController.class)
@Import(SecurityConfig.class)
class PeopleControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PeopleService peopleService;

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

    // ---------------------------------------------------------------- GET /api/v1/people

    @Test
    @DisplayName("사람 목록 조회 시 200과 함께 서비스 결과를 응답 JSON으로 변환하고 id는 문자열로 직렬화한다")
    void people_success() throws Exception {
        // given: 서비스가 사람 1명과 다음 커서를 반환
        given(peopleService.people(1L, null, null))
                .willReturn(new PeopleResult(
                        List.of(new PeopleItemResult(
                                42L,
                                "길동",
                                new ProfilePhotoInfo("profile/42/key", "https://cdn.example.com/signed",
                                        new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200)),
                                55,
                                12,
                                RelationshipType.FRIEND,
                                RelationshipSpecificType.GO_TO,
                                3,
                                7L,
                                true,
                                new PeopleIntimacyQuizStatusResult(true, false))),
                        PeopleThresholdResult.of(),
                        new PeoplePageResult(42L, true)));

        // when: 인증 상태로 사람 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + userId/chatRoomId/nextCursor가 문자열로 직렬화된 JSON 응답
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.people[0].userId").value("42"))
                .andExpect(jsonPath("$.people[0].userName").value("길동"))
                .andExpect(jsonPath("$.people[0].profilePhoto.key").value("profile/42/key"))
                .andExpect(jsonPath("$.people[0].profilePhoto.photoUrl").value("https://cdn.example.com/signed"))
                .andExpect(jsonPath("$.people[0].profilePhoto.position.startPos.x").value(10))
                .andExpect(jsonPath("$.people[0].profilePhoto.position.startPos.y").value(20))
                .andExpect(jsonPath("$.people[0].profilePhoto.position.width").value(100))
                .andExpect(jsonPath("$.people[0].profilePhoto.position.height").value(200))
                .andExpect(jsonPath("$.people[0].intimacy").value(55))
                .andExpect(jsonPath("$.people[0].gameIntimacy").value(12))
                .andExpect(jsonPath("$.people[0].relationshipType").value("friend"))
                .andExpect(jsonPath("$.people[0].relationshipSpecificType").value("자주 찾는 사이"))
                .andExpect(jsonPath("$.people[0].sceneElementCount").value(3))
                .andExpect(jsonPath("$.people[0].chatRoomId").value("7"))
                .andExpect(jsonPath("$.people[0].isFavorited").value(true))
                .andExpect(jsonPath("$.people[0].intimacyQuizStatus.myAnswered").value(true))
                .andExpect(jsonPath("$.people[0].intimacyQuizStatus.partnerAnswered").value(false))
                .andExpect(jsonPath("$.people[0].isHighlighted").doesNotExist())
                .andExpect(jsonPath("$.threshold.acquaintance").value(0))
                .andExpect(jsonPath("$.threshold.friend").value(35))
                .andExpect(jsonPath("$.threshold.bestFriend").value(70))
                .andExpect(jsonPath("$.page.nextCursor").value("42"))
                .andExpect(jsonPath("$.page.hasMore").value(true));
        then(peopleService).should().people(1L, null, null);
    }

    @Test
    @DisplayName("사람 목록 조회 시 cursor는 Long으로 변환되어 limit과 함께 서비스로 전달된다")
    void people_with_cursor_and_limit() throws Exception {
        // given: 서비스가 빈 목록을 반환
        given(peopleService.people(1L, 10L, 5))
                .willReturn(new PeopleResult(List.of(), PeopleThresholdResult.of(), new PeoplePageResult(null, false)));

        // when: cursor/limit 쿼리 파라미터와 함께 사람 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people")
                .param("cursor", "10")
                .param("limit", "5")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + cursor는 Long, limit은 Integer로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.people").isEmpty())
                .andExpect(jsonPath("$.page.nextCursor").isEmpty())
                .andExpect(jsonPath("$.page.hasMore").value(false));
        then(peopleService).should().people(1L, 10L, 5);
    }

    @Test
    @DisplayName("사람 목록 조회 시 cursor가 숫자가 아니면 400 INVALID_REQUEST를 반환하고 서비스를 호출하지 않는다")
    void people_with_non_numeric_cursor_returns_400() throws Exception {
        // when: cursor를 숫자가 아닌 값으로 사람 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people")
                .param("cursor", "abc")
                .header("Authorization", "Bearer access-token"));

        // then: 400 INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(peopleService).should(never()).people(any(), any(), any());
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void people_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 사람 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(peopleService).should(never()).people(any(), any(), any());
    }

    // ------------------------------------------------- GET /api/v1/people/{userId}/profile

    @Test
    @DisplayName("프로필 조회 시 200과 함께 공개 동의된 필드만 담긴 응답 JSON을 반환한다")
    void profile_success() throws Exception {
        // given: 소속만 공개 동의된 상대의 프로필을 서비스가 반환
        given(peopleService.profile(1L, 42L))
                .willReturn(new PeopleProfileResult(
                        42L,
                        "홍길동",
                        null,
                        80,
                        20,
                        RelationshipType.BEST_FRIEND,
                        RelationshipSpecificType.SPECIAL,
                        true,
                        new PeopleProfileDisclosedFieldsResult("트윈리대학교", null),
                        false,
                        true));

        // when: 인증 상태로 프로필 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/profile", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 미동의 필드는 null인 JSON 응답 + v2에서 추가된 성별·소속·출생연도는 없음 + 인증 유저 id·경로 userId로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.userName").value("홍길동"))
                .andExpect(jsonPath("$.gender").doesNotExist())
                .andExpect(jsonPath("$.organization").doesNotExist())
                .andExpect(jsonPath("$.birthYear").doesNotExist())
                .andExpect(jsonPath("$.profilePhoto").isEmpty())
                .andExpect(jsonPath("$.intimacy").value(80))
                .andExpect(jsonPath("$.gameIntimacy").value(20))
                .andExpect(jsonPath("$.relationshipType").value("bestFriend"))
                .andExpect(jsonPath("$.relationshipSpecificType").value("특별한 사이"))
                .andExpect(jsonPath("$.isFavorited").value(true))
                .andExpect(jsonPath("$.isHighlighted").doesNotExist())
                .andExpect(jsonPath("$.disclosedFields.affiliation").value("트윈리대학교"))
                .andExpect(jsonPath("$.disclosedFields.affiliationNumber").isEmpty())
                .andExpect(jsonPath("$.isDeleted").value(false))
                .andExpect(jsonPath("$.isBlocked").value(true));
        then(peopleService).should().profile(1L, 42L);
    }

    @Test
    @DisplayName("프로필 조회 시 경로 변수 userId가 숫자가 아니면 400 INVALID_REQUEST를 반환하고 서비스를 호출하지 않는다")
    void profile_with_non_numeric_userId_returns_400() throws Exception {
        // when: 경로 변수 userId를 숫자가 아닌 값으로 프로필 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/profile", "abc")
                .header("Authorization", "Bearer access-token"));

        // then: 400 INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(peopleService).should(never()).profile(any(), any());
    }

    // ------------------------------------------------- GET /api/v2/people/{userId}/profile

    @Test
    @DisplayName("프로필 조회 v2 시 200과 함께 성별·소속·출생연도를 포함한 응답 JSON을 반환한다")
    void profileV2_success() throws Exception {
        // given: 소속만 공개 동의된 상대의 프로필을 서비스가 반환
        given(peopleService.profileV2(1L, 42L))
                .willReturn(new PeopleProfileV2Result(
                        42L,
                        "홍길동",
                        Gender.FEMALE,
                        "트윈리대",
                        "02",
                        null,
                        80,
                        20,
                        RelationshipType.BEST_FRIEND,
                        RelationshipSpecificType.SPECIAL,
                        true,
                        new PeopleProfileDisclosedFieldsResult("트윈리대학교", null),
                        false,
                        true));

        // when: 인증 상태로 프로필 조회 v2 API 호출
        var result = mockMvc.perform(get("/api/v2/people/{userId}/profile", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + v1 필드에 성별·소속·출생연도가 더해진 JSON 응답 + 인증 유저 id·경로 userId로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.userName").value("홍길동"))
                .andExpect(jsonPath("$.gender").value("female"))
                .andExpect(jsonPath("$.organization").value("트윈리대"))
                .andExpect(jsonPath("$.birthYear").value("02"))
                .andExpect(jsonPath("$.profilePhoto").isEmpty())
                .andExpect(jsonPath("$.intimacy").value(80))
                .andExpect(jsonPath("$.gameIntimacy").value(20))
                .andExpect(jsonPath("$.relationshipType").value("bestFriend"))
                .andExpect(jsonPath("$.relationshipSpecificType").value("특별한 사이"))
                .andExpect(jsonPath("$.isFavorited").value(true))
                .andExpect(jsonPath("$.disclosedFields.affiliation").value("트윈리대학교"))
                .andExpect(jsonPath("$.disclosedFields.affiliationNumber").isEmpty())
                .andExpect(jsonPath("$.isDeleted").value(false))
                .andExpect(jsonPath("$.isBlocked").value(true));
        then(peopleService).should().profileV2(1L, 42L);
    }

    @Test
    @DisplayName("프로필 조회 v2 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void profileV2_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 프로필 조회 v2 API 호출
        var result = mockMvc.perform(get("/api/v2/people/{userId}/profile", "42"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(peopleService).should(never()).profileV2(any(), any());
    }

    // ----------------------------------------------- PUT/DELETE /api/v1/people/{userId}/favorite

    @Test
    @DisplayName("즐겨찾기 등록 성공 시 200을 반환하고 인증 유저 id와 경로의 userId로 서비스를 호출한다")
    void favorite_success() throws Exception {
        // when: 인증 상태로 즐겨찾기 등록 API 호출
        var result = mockMvc.perform(put("/api/v1/people/{userId}/favorite", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 인증 유저 id·경로 userId로 서비스에 위임
        result.andExpect(status().isOk());
        then(peopleService).should().favorite(1L, 42L);
    }

    @Test
    @DisplayName("즐겨찾기 해제 성공 시 200을 반환하고 인증 유저 id와 경로의 userId로 서비스를 호출한다")
    void deleteFavorites_success() throws Exception {
        // when: 인증 상태로 즐겨찾기 해제 API 호출
        var result = mockMvc.perform(delete("/api/v1/people/{userId}/favorite", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 인증 유저 id·경로 userId로 서비스에 위임
        result.andExpect(status().isOk());
        then(peopleService).should().deleteFavorite(1L, 42L);
    }

    // ------------------------------------------ GET /api/v1/people/{userId}/intimacy-series

    @Test
    @DisplayName("친밀도 시계열 조회 시 쿼리 파라미터 없이 인증 유저 id와 경로 userId만으로 위임하고 200과 함께 시계열 JSON을 반환한다")
    void intimacySeries_success() throws Exception {
        // given: 서비스가 현재 친밀도와 시계열 1건을 반환
        given(peopleService.intimacySeries(1L, 42L))
                .willReturn(new PeopleIntimacySeriesResult(
                        55,
                        10,
                        List.of(new PeopleIntimacySeriesItemResult(LocalDate.of(2026, 7, 1), 40, 5))));

        // when: 인증 상태로 친밀도 시계열 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-series", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 시계열 JSON 응답 + 인증 유저 id·경로 userId로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentIntimacy").value(55))
                .andExpect(jsonPath("$.currentGameIntimacy").value(10))
                .andExpect(jsonPath("$.intimacySeries[0].date").value("2026-07-01"))
                .andExpect(jsonPath("$.intimacySeries[0].intimacy").value(40))
                .andExpect(jsonPath("$.intimacySeries[0].gameIntimacy").value(5));
        then(peopleService).should().intimacySeries(1L, 42L);
    }

    // ------------------------------------------------- GET /api/v1/people/{userId}/events

    @Test
    @DisplayName("이벤트 목록 조회 시 200과 함께 상대 정보·이벤트·페이지 JSON을 반환하고 cursor/limit을 그대로 위임한다")
    void events_success() throws Exception {
        // given: 서비스가 상대 정보와 이벤트 1건, 다음 커서를 반환
        given(peopleService.events(1L, 42L, LocalDate.of(2026, 7, 20), 5))
                .willReturn(new PeopleEventsResult(
                        new PeopleEventsPartnerResult(42L, "홍길동", null, 45, 6, RelationshipSpecificType.CLOSE),
                        List.of(new PeopleEventsItemResult(
                                LocalDate.of(2026, 7, 19),
                                RelationshipSpecificType.CLOSE,
                                5,
                                "카페",
                                "CAFE",
                                "커피를 마셨다")),
                        new PeopleEventsPageResult(LocalDate.of(2026, 7, 19), true)));

        // when: cursor/limit과 함께 이벤트 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/events", "42")
                .param("cursor", "2026-07-20")
                .param("limit", "5")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + 상대·이벤트·페이지 JSON 응답 + cursor는 LocalDate로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.partner.userId").value("42"))
                .andExpect(jsonPath("$.partner.userName").value("홍길동"))
                .andExpect(jsonPath("$.partner.intimacy").value(45))
                .andExpect(jsonPath("$.partner.gameIntimacy").value(6))
                .andExpect(jsonPath("$.partner.relationshipSpecificType").value("친한 사이"))
                .andExpect(jsonPath("$.events[0].date").value("2026-07-19"))
                .andExpect(jsonPath("$.events[0].relationshipChange").value("친한 사이"))
                .andExpect(jsonPath("$.events[0].intimacyDelta").value(5))
                .andExpect(jsonPath("$.events[0].place").value("카페"))
                .andExpect(jsonPath("$.events[0].placeCode").value("CAFE"))
                .andExpect(jsonPath("$.events[0].preview").value("커피를 마셨다"))
                .andExpect(jsonPath("$.page.nextCursor").value("2026-07-19"))
                .andExpect(jsonPath("$.page.hasMore").value(true));
        then(peopleService).should().events(1L, 42L, LocalDate.of(2026, 7, 20), 5);
    }

    // -------------------------------------------- GET /api/v1/people/{userId}/events/{date}

    @Test
    @DisplayName("이벤트 상세 조회 시 200과 함께 씬 타입이 type 필드로 구분된 JSON을 반환한다")
    void event_success() throws Exception {
        // given: 서비스가 action 타입 씬 1건을 반환
        given(peopleService.event(1L, 42L, LocalDate.of(2026, 7, 20)))
                .willReturn(new PeopleEventResult(
                        LocalDate.of(2026, 7, 20),
                        42L,
                        "v1",
                        List.of(new PeopleEventActionSceneResult(
                                100L,
                                "action",
                                OffsetDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneOffset.ofHours(9)),
                                OffsetDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
                                "학교 복도",
                                "SCHOOL_HALLWAY",
                                List.of(42L),
                                "복도를 함께 걸었다",
                                "조금 설렜다")),
                        List.of(new PeopleEventUserInfoResult(42L, "홍길동",
                                new ProfilePhotoInfo("profile/42/key", "https://cdn.example.com/signed",
                                        new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200))))));

        // when: 날짜 경로 변수와 함께 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/events/{date}", "42", "2026-07-20")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + type이 action인 씬 JSON 응답 + 인증 유저 id·userId·date로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-07-20"))
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.scenes[0].sceneId").value("100"))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].place").value("학교 복도"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("SCHOOL_HALLWAY"))
                .andExpect(jsonPath("$.scenes[0].with[0]", is("42")))
                .andExpect(jsonPath("$.scenes[0].narration").value("복도를 함께 걸었다"))
                .andExpect(jsonPath("$.scenes[0].mind").value("조금 설렜다"))
                .andExpect(jsonPath("$.userInfos[0].userId").value("42"))
                .andExpect(jsonPath("$.userInfos[0].userName").value("홍길동"))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto.key").value("profile/42/key"))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto.photoUrl").value("https://cdn.example.com/signed"))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto.position.startPos.x").value(10))
                .andExpect(jsonPath("$.userInfos[0].profilePhoto.position.height").value(200));
        then(peopleService).should().event(1L, 42L, LocalDate.of(2026, 7, 20));
    }

    @Test
    @DisplayName("이벤트 상세 조회 시 date 경로 변수가 날짜 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void event_with_invalid_date_returns_400() throws Exception {
        // when: date 경로 변수를 날짜가 아닌 값으로 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/events/{date}", "42", "not-a-date")
                .header("Authorization", "Bearer access-token"));

        // then: 400 INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(peopleService).should(never()).event(any(), any(), any());
    }

    @Test
    @DisplayName("v1 이벤트 상세 조회는 상대와 함께한 이동을 도착지 기준 action 씬으로 바꿔 내린다")
    void event_v1_converts_move_scene_to_action() throws Exception {
        // given: 서비스가 상대(42)와 함께 이동한 move 씬 1건을 반환
        given(peopleService.event(1L, 42L, LocalDate.of(2026, 7, 20)))
                .willReturn(moveEventResult(List.of(42L)));

        // when: 출시된 앱이 쓰는 v1 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/events/{date}", "42", "2026-07-20")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + 기존 action 모양(장소는 도착지)으로 내려가고 move 전용 필드는 없다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes", hasSize(1)))
                .andExpect(jsonPath("$.scenes[0].type").value("action"))
                .andExpect(jsonPath("$.scenes[0].place").value("학생 거리 탐앤탐스"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("P0158"))
                .andExpect(jsonPath("$.scenes[0].with[0]", is("42")))
                .andExpect(jsonPath("$.scenes[0].narration").value("카페로 향했다"))
                .andExpect(jsonPath("$.scenes[0].fromPlace").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].travelMode").doesNotExist());
    }

    // -------------------------------------------- GET /api/v2/people/{userId}/events/{date}

    @Test
    @DisplayName("v2 이벤트 상세 조회는 move 씬을 출발지·도착지·이동 수단과 함께 내리고 eventV2 에 위임한다")
    void eventV2_success() throws Exception {
        // given: 서비스가 혼자 이동한 move 씬 1건을 반환
        given(peopleService.eventV2(1L, 42L, LocalDate.of(2026, 7, 20)))
                .willReturn(moveEventResult(List.of()));

        // when: 새 앱이 쓰는 v2 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/people/{userId}/events/{date}", "42", "2026-07-20")
                .header("Authorization", "Bearer access-token"));

        // then: 200 + move 모양으로 내려가고 혼자 간 길의 with 는 빈 배열이다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-07-20"))
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.scenes[0].sceneId").value("200"))
                .andExpect(jsonPath("$.scenes[0].type").value("move"))
                .andExpect(jsonPath("$.scenes[0].startsAt").value("2026-07-20T11:00:00+09:00"))
                .andExpect(jsonPath("$.scenes[0].endsAt").value("2026-07-20T11:10:00+09:00"))
                .andExpect(jsonPath("$.scenes[0].fromPlace").value("집"))
                .andExpect(jsonPath("$.scenes[0].fromPlaceCode").value("P0258"))
                .andExpect(jsonPath("$.scenes[0].place").value("학생 거리 탐앤탐스"))
                .andExpect(jsonPath("$.scenes[0].placeCode").value("P0158"))
                .andExpect(jsonPath("$.scenes[0].with", hasSize(0)))
                .andExpect(jsonPath("$.scenes[0].travelMode").value("walk"))
                .andExpect(jsonPath("$.scenes[0].mapVersion").doesNotExist())
                .andExpect(jsonPath("$.scenes[0].narration").value("카페로 향했다"))
                .andExpect(jsonPath("$.userInfos[0].userId").value("1"));
        then(peopleService).should().eventV2(1L, 42L, LocalDate.of(2026, 7, 20));
        then(peopleService).should(never()).event(any(), any(), any());
    }

    @Test
    @DisplayName("v2 이벤트 상세 조회 시 경로 변수 userId가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void eventV2_with_non_numeric_userId_returns_400() throws Exception {
        // when: userId 를 숫자가 아닌 값으로 v2 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/people/{userId}/events/{date}", "abc", "2026-07-20")
                .header("Authorization", "Bearer access-token"));

        // then: 400 INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(peopleService).should(never()).eventV2(any(), any(), any());
    }

    @Test
    @DisplayName("v2 이벤트 상세 조회 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void eventV2_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 v2 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/people/{userId}/events/{date}", "42", "2026-07-20"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(peopleService).should(never()).eventV2(any(), any(), any());
    }

    @Test
    @DisplayName("v2 이벤트 상세 조회 시 date 경로 변수가 날짜 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void eventV2_with_invalid_date_returns_400() throws Exception {
        // when: date 를 날짜가 아닌 값으로 v2 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/people/{userId}/events/{date}", "42", "not-a-date")
                .header("Authorization", "Bearer access-token"));

        // then: 400 INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(peopleService).should(never()).eventV2(any(), any(), any());
    }

    @Test
    @DisplayName("v2 이벤트 상세 조회 시 서비스가 USER_NOT_FOUND를 던지면 404를 반환한다")
    void eventV2_user_not_found_returns_404() throws Exception {
        // given: 상대 유저가 없음
        given(peopleService.eventV2(1L, 42L, LocalDate.of(2026, 7, 20)))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        // when: v2 이벤트 상세 조회 API 호출
        var result = mockMvc.perform(get("/api/v2/people/{userId}/events/{date}", "42", "2026-07-20")
                .header("Authorization", "Bearer access-token"));

        // then: 404 USER_NOT_FOUND 반환
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private PeopleEventResult moveEventResult(List<Long> with) {
        return new PeopleEventResult(
                LocalDate.of(2026, 7, 20),
                42L,
                "v1",
                List.of(new PeopleEventMoveSceneResult(
                        200L,
                        "move",
                        OffsetDateTime.of(2026, 7, 20, 11, 0, 0, 0, ZoneOffset.ofHours(9)),
                        OffsetDateTime.of(2026, 7, 20, 11, 10, 0, 0, ZoneOffset.ofHours(9)),
                        "집",
                        "P0258",
                        "학생 거리 탐앤탐스",
                        "P0158",
                        with,
                        "walk",
                        null,
                        "카페로 향했다",
                        null)),
                List.of(new PeopleEventUserInfoResult(1L, "자신", null)));
    }

    // ------------------------------------------ GET /api/v1/people/{userId}/learned-facts

    @Test
    @DisplayName("알게 된 사실 조회 시 200과 함께 learnedFacts JSON을 반환한다")
    void learnedFacts_success() throws Exception {
        // given: 서비스가 상대에 대해 알게 된 사실을 반환
        given(peopleService.learnedFacts(1L, 42L))
                .willReturn(new PeopleLearnedFactsResult("커피를 좋아한다"));

        // when: 인증 상태로 알게 된 사실 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/people/{userId}/learned-facts", "42")
                .header("Authorization", "Bearer access-token"));

        // then: 200 반환 + learnedFacts JSON 응답 + 인증 유저 id·경로 userId로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.learnedFacts").value("커피를 좋아한다"));
        then(peopleService).should().learnedFacts(1L, 42L);
    }

    @Test
    @DisplayName("사람 목록 조회 시 limit이 허용 범위(1~100) 밖이면 400을 반환하고 서비스를 호출하지 않는다")
    void people_with_out_of_range_limit_returns_400() throws Exception {
        // when & then: 0과 상한 초과 모두 입력 단계에서 막힌다
        for (String limit : List.of("0", "101")) {
            mockMvc.perform(get("/api/v1/people")
                            .param("limit", limit)
                            .header("Authorization", "Bearer access-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        then(peopleService).should(never()).people(any(), any(), any());
    }

    // ------------------------------------- GET /api/v1/people/{userId}/intimacy-quiz/match-rate

    @Test
    @DisplayName("친밀도 퀴즈 일치도 조회 시 200과 함께 비교 회차·일치 회차·일치도를 내려준다")
    void intimacyQuizMatchRate_success() throws Exception {
        // given
        given(peopleService.intimacyQuizMatchRate(1L, 42L)).willReturn(new PeopleIntimacyQuizMatchRateResult(12, 7, 58.3));

        // when
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz/match-rate", "42")
                .header("Authorization", "Bearer access-token"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.comparedCount").value(12))
                .andExpect(jsonPath("$.matchedCount").value(7))
                .andExpect(jsonPath("$.matchRate").value(58.3));
        then(peopleService).should().intimacyQuizMatchRate(1L, 42L);
    }

    @Test
    @DisplayName("비교한 회차가 없으면 matchRate 를 null 로 내려준다")
    void intimacyQuizMatchRate_without_comparison() throws Exception {
        // given
        given(peopleService.intimacyQuizMatchRate(1L, 42L)).willReturn(new PeopleIntimacyQuizMatchRateResult(0, 0, null));

        // when
        var result = mockMvc.perform(get("/api/v1/people/{userId}/intimacy-quiz/match-rate", "42")
                .header("Authorization", "Bearer access-token"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.comparedCount").value(0))
                .andExpect(jsonPath("$.matchRate").isEmpty());
    }
}

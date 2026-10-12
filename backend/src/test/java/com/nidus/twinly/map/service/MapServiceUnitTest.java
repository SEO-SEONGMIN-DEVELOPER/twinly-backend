package com.nidus.twinly.map.service;

import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.SceneRepository.SceneRouteProjection;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.map.dto.result.MapTwinResult;
import com.nidus.twinly.map.dto.result.MapTwinSceneResult;
import com.nidus.twinly.map.dto.result.MapTwinsResult;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository.PartnerFirstUpdateProjection;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MapServiceUnitTest {

    private static final Long ME = 1L;
    private static final Long SEASON_ID = 7L;
    private static final String ANONYMOUS = "누군가의 트윈";

    @Mock
    SceneRepository sceneRepository;

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @InjectMocks
    MapService mapService;

    private final LocalDateTime now = KstTimes.now();
    private final LocalDate today = now.toLocalDate();

    @Test
    @DisplayName("후보가 없으면 빈 목록과 KST serverNow 를 돌려주고, 후보 조회는 오늘 0시 이전 참여·24시간 범위 조건으로 위임한다")
    void twins_without_candidates_returns_empty() {
        // given: 후보 조회 결과가 비어 있다
        givenCandidates();

        // when: 지도 트윈 조회
        MapTwinsResult result = mapService.twins(ME);

        // then: 빈 목록 + serverNow 는 +09:00
        assertThat(result.twins()).isEmpty();
        assertThat(result.serverNow().getOffset()).isEqualTo(ZoneOffset.ofHours(9));

        // then: 참여 기준은 오늘 KST 0시, 장면 범위는 24시간 전부터 오늘까지(날짜는 하루 여유)
        ArgumentCaptor<Instant> participatedBefore = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<LocalDate> fromDate = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toDate = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDateTime> endsAfter = ArgumentCaptor.forClass(LocalDateTime.class);
        then(seasonParticipationRepository).should().findAllMapCandidateUserIds(eq(ME), eq(SEASON_ID),
                participatedBefore.capture(), fromDate.capture(), toDate.capture(), endsAfter.capture());
        assertThat(participatedBefore.getValue()).isEqualTo(KstTimes.toInstant(today.atStartOfDay()));
        assertThat(toDate.getValue()).isEqualTo(today);
        assertThat(endsAfter.getValue()).isBetween(now.minusHours(24).minusMinutes(1), now.minusHours(24).plusMinutes(1));
        assertThat(fromDate.getValue()).isEqualTo(endsAfter.getValue().toLocalDate().minusDays(1));

        // then: 후보가 없으니 더 조회하지 않는다
        then(relationshipRepository).should(never()).findAllPartnerFirstUpdatesByUserId(any());
        then(sceneRepository).should(never()).findAllRoutesByUserIdInAndEndsAfter(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("인연은 내 장면에 나와도 닉네임으로 넣고, 인연이 아닌데 내 오늘 이후 장면에 나오는 사람은 빼며, 접점 없는 트윈은 누군가의 트윈으로 넣는다")
    void twins_classifies_relationship_met_and_anonymous() {
        // given: 후보 10(어제 생긴 인연, 내 장면 동행), 20(인연 아님, 내 미래 장면 동행), 30(접점 없음)
        givenCandidates(10L, 20L, 30L);
        givenPartners(new Partner(10L, today.minusDays(1).atTime(9, 0)));
        givenRoutes(List.of(
                route(100L, ME, SceneType.DIALOGUE, now.plusHours(2), now.plusHours(3), "P0158", null, null),
                route(110L, 10L, SceneType.ACTION, now.minusHours(1), now, "P0012", null, null),
                route(120L, 20L, SceneType.ACTION, now.minusHours(1), now, "P0012", null, null),
                route(130L, 30L, SceneType.ACTION, now.minusHours(1), now, "P0012", null, null)));
        given(scenePartnerRepository.findAllBySceneIdIn(List.of(100L)))
                .willReturn(List.of(ScenePartner.create(100L, 10L), ScenePartner.create(100L, 20L)));
        givenUsers(user(10L, "서연"));

        // when: 지도 트윈 조회
        MapTwinsResult result = mapService.twins(ME);

        // then: 인연(10)이 먼저 닉네임으로, 접점 없는 30 은 누군가의 트윈으로, 20 은 빠진다
        assertThat(result.twins()).extracting(MapTwinResult::userId).containsExactly(10L, 30L);
        assertThat(result.twins()).extracting(MapTwinResult::displayName).containsExactly("서연", ANONYMOUS);
    }

    @Test
    @DisplayName("오늘 0시 이후에 처음 생긴 인연은 인연으로도 접점 없는 트윈으로도 넣지 않는다")
    void twins_excludes_relationship_created_today() {
        // given: 40 은 오늘 0시 정각에 처음 인연이 됐고, 30 은 접점 없는 트윈이다
        givenCandidates(30L, 40L);
        givenPartners(new Partner(40L, today.atStartOfDay()));
        givenRoutes(List.of(
                route(130L, 30L, SceneType.ACTION, now.minusHours(1), now, "P0012", null, null),
                route(140L, 40L, SceneType.ACTION, now.minusHours(1), now, "P0012", null, null)));

        // when: 지도 트윈 조회
        MapTwinsResult result = mapService.twins(ME);

        // then: 30 만 누군가의 트윈으로 나오고, 40 은 이름이 미리 드러나지도 익명으로 나오지도 않는다
        assertThat(result.twins()).extracting(MapTwinResult::userId).containsExactly(30L);
        assertThat(result.twins()).extracting(MapTwinResult::displayName).containsExactly(ANONYMOUS);
    }

    @Test
    @DisplayName("인연을 먼저 채우고 남는 자리를 접점 없는 트윈으로 채워 최대 20명을 돌려준다")
    void twins_fills_relationships_first_up_to_twenty() {
        // given: 어제 이전 인연 5명(101~105)과 접점 없는 트윈 30명(201~230)
        List<Long> relationshipIds = LongStream.rangeClosed(101, 105).boxed().toList();
        List<Long> anonymousIds = LongStream.rangeClosed(201, 230).boxed().toList();
        List<Long> candidates = new ArrayList<>(relationshipIds);
        candidates.addAll(anonymousIds);
        givenCandidates(candidates.toArray(Long[]::new));
        givenPartners(relationshipIds.stream()
                .map(id -> new Partner(id, today.minusDays(3).atTime(12, 0)))
                .toArray(Partner[]::new));
        givenRoutes(candidates.stream()
                .map(id -> route(id * 10, id, SceneType.ACTION, now.minusHours(1), now, "P0012", null, null))
                .toList());

        // when: 지도 트윈 조회
        MapTwinsResult result = mapService.twins(ME);

        // then: 20명, 앞 5명은 인연 전원, 나머지 15명은 접점 없는 트윈
        List<Long> userIds = result.twins().stream().map(MapTwinResult::userId).toList();
        assertThat(userIds).hasSize(20).doesNotHaveDuplicates();
        assertThat(userIds.subList(0, 5)).containsExactlyInAnyOrderElementsOf(relationshipIds);
        assertThat(userIds.subList(5, 20)).allMatch(anonymousIds::contains);
    }

    @Test
    @DisplayName("장소 없는 장면과 길이 0 장면은 빼고, 남는 장면이 없는 트윈은 빼며, move 가 아니면 출발지·이동 수단은 null 이다")
    void twins_filters_and_shapes_scenes() {
        // given: 30 은 그릴 수 있는 장면과 없는 장면이 섞여 있고, 31 은 장소 없는 장면뿐이다
        givenCandidates(30L, 31L);
        LocalDateTime t0 = now.minusHours(3);
        givenRoutes(List.of(
                route(301L, 30L, SceneType.ACTION, t0, t0.plusMinutes(30), null, null, null),
                route(302L, 30L, SceneType.ACTION, t0.plusMinutes(30), t0.plusMinutes(30), "P0012", null, null),
                route(303L, 30L, SceneType.MOVE, t0.plusMinutes(30), t0.plusMinutes(40), "P0158", "P0012", "walk"),
                route(304L, 30L, SceneType.ACTION, t0.plusMinutes(40), t0.plusMinutes(90), "P0158", "P0001", "transit"),
                route(311L, 31L, SceneType.ACTION, t0, t0.plusMinutes(30), null, null, null)));

        // when: 지도 트윈 조회
        MapTwinsResult result = mapService.twins(ME);

        // then: 31 은 빠지고, 30 의 장면은 move·action 두 개만 시작 순서대로 남는다
        assertThat(result.twins()).extracting(MapTwinResult::userId).containsExactly(30L);
        assertThat(result.twins().get(0).scenes()).containsExactly(
                new MapTwinSceneResult("move",
                        KstTimes.toKstOffsetDateTime(t0.plusMinutes(30)),
                        KstTimes.toKstOffsetDateTime(t0.plusMinutes(40)),
                        "P0158", "P0012", "walk"),
                new MapTwinSceneResult("action",
                        KstTimes.toKstOffsetDateTime(t0.plusMinutes(40)),
                        KstTimes.toKstOffsetDateTime(t0.plusMinutes(90)),
                        "P0158", null, null));
    }

    @Test
    @DisplayName("인연의 닉네임이 비어 있으면 누군가의 트윈으로 표시한다")
    void twins_blank_nickname_falls_back_to_anonymous() {
        // given: 어제 생긴 인연 10 의 닉네임이 공백이다
        givenCandidates(10L);
        givenPartners(new Partner(10L, today.minusDays(1).atTime(9, 0)));
        givenRoutes(List.of(route(110L, 10L, SceneType.ACTION, now.minusHours(1), now, "P0012", null, null)));
        User blank = mock(User.class);
        given(blank.displayNickname()).willReturn(" ");
        given(userRepository.findAllById(List.of(10L))).willReturn(List.of(blank));

        // when: 지도 트윈 조회
        MapTwinsResult result = mapService.twins(ME);

        // then: 빈 표시명 대신 누군가의 트윈
        assertThat(result.twins()).extracting(MapTwinResult::displayName).containsExactly(ANONYMOUS);
    }

    @Test
    @DisplayName("같은 날 다시 조회하면 같은 순서이고, 후보 한 명이 빠져도 나머지의 상대 순서는 그대로다")
    void twins_daily_order_is_stable() {
        // given: 접점 없는 트윈 10명
        List<Long> candidates = LongStream.rangeClosed(301, 310).boxed().toList();
        givenCandidates(candidates.toArray(Long[]::new));
        givenRoutes(candidates.stream()
                .map(id -> route(id * 10, id, SceneType.ACTION, now.minusHours(1), now, "P0012", null, null))
                .toList());
        List<Long> first = userIds(mapService.twins(ME));
        List<Long> again = userIds(mapService.twins(ME));

        // given: 그중 한 명(305)이 차단·탈퇴 등으로 후보에서 빠진다
        given(seasonParticipationRepository.findAllMapCandidateUserIds(eq(ME), eq(SEASON_ID), any(), any(), any(), any()))
                .willReturn(candidates.stream().filter(id -> id != 305L).toList());

        // when: 다시 조회
        List<Long> afterExclusion = userIds(mapService.twins(ME));

        // then: 재조회 순서가 같고, 빠진 사람만 사라진 같은 순서다
        assertThat(again).isEqualTo(first);
        assertThat(afterExclusion).isEqualTo(first.stream().filter(id -> id != 305L).toList());
    }

    private void givenCandidates(Long... userIds) {
        Season season = Season.create(Instant.now(), Instant.now());
        ReflectionTestUtils.setField(season, "id", SEASON_ID);
        given(currentSeasonReader.read()).willReturn(season);
        given(seasonParticipationRepository.findAllMapCandidateUserIds(eq(ME), eq(SEASON_ID), any(), any(), any(), any()))
                .willReturn(List.of(userIds));
    }

    private void givenPartners(Partner... partners) {
        given(relationshipRepository.findAllPartnerFirstUpdatesByUserId(ME)).willReturn(List.of(partners));
    }

    private void givenRoutes(List<SceneRouteProjection> routes) {
        given(sceneRepository.findAllRoutesByUserIdInAndEndsAfter(anyList(), any(), any(), any()))
                .willAnswer(invocation -> {
                    List<Long> userIds = invocation.getArgument(0);
                    return routes.stream().filter(route -> userIds.contains(route.getUserId())).toList();
                });
    }

    private void givenUsers(User... users) {
        given(userRepository.findAllById(any())).willReturn(List.of(users));
    }

    private User user(Long id, String nickname) {
        User user = mock(User.class);
        given(user.getId()).willReturn(id);
        given(user.displayNickname()).willReturn(nickname);
        return user;
    }

    private List<Long> userIds(MapTwinsResult result) {
        return result.twins().stream().map(MapTwinResult::userId).toList();
    }

    private SceneRouteProjection route(Long id, Long userId, SceneType type, LocalDateTime startsAt, LocalDateTime endsAt,
                        String placeCode, String fromPlaceCode, String travelMode) {
        return new Route(id, userId, type, startsAt, endsAt, placeCode, fromPlaceCode, travelMode);
    }

    private record Route(Long id, Long userId, SceneType type, LocalDateTime startsAt, LocalDateTime endsAt,
                         String placeCode, String fromPlaceCode, String travelMode) implements SceneRouteProjection {

        public Long getId() { return id; }
        public Long getUserId() { return userId; }
        public SceneType getType() { return type; }
        public LocalDateTime getStartsAt() { return startsAt; }
        public LocalDateTime getEndsAt() { return endsAt; }
        public String getPlaceCode() { return placeCode; }
        public String getFromPlaceCode() { return fromPlaceCode; }
        public String getTravelMode() { return travelMode; }
    }

    private record Partner(Long partnerUserId, LocalDateTime firstUpdateTime) implements PartnerFirstUpdateProjection {

        public Long getPartnerUserId() { return partnerUserId; }
        public LocalDateTime getFirstUpdateTime() { return firstUpdateTime; }
    }
}

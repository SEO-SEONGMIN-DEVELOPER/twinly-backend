package com.nidus.twinly.world.service;

import com.nidus.twinly.season.domain.SeasonScope;
import org.junit.jupiter.api.BeforeEach;
import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.relationship.repository.RelationshipRepository.PartnerFirstUpdateProjection;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import com.nidus.twinly.world.dto.result.WorldSectorsResult;
import com.nidus.twinly.world.entity.WorldTrack;
import com.nidus.twinly.world.reader.WorldTrackSlice;
import com.nidus.twinly.world.reader.WorldTrackWindowReader;
import com.nidus.twinly.world.repository.WorldTrackRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WorldServiceUnitTest {

    private static final Long ME = 12L;
    private static final int POOL_NUMBER = 7;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 16);
    private static final String SECTOR_CODE = "S034";
    private static final String FROM = "2026-10-16T13:00:00+09:00";
    private static final String TO = "2026-10-16T14:00:00+09:00";
    private static final byte[] PUBLIC_BODY = {1};
    private static final byte[] PERSONAL_BODY = {2};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    WorldTrackRepository worldTrackRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @Mock
    CurrentSeasonReader currentSeasonReader;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    BlockRepository blockRepository;

    @Mock
    SceneRepository sceneRepository;

    @Mock
    WorldTrackWindowReader worldTrackWindowReader;

    // 합치기와 이름·시각 붙이기는 진짜 구현이 돌아야 응답 모양을 검증할 수 있다.
    @Spy
    WorldTrackMerger worldTrackMerger = new WorldTrackMerger(MAPPER);

    @Spy
    WorldTrackPublisher worldTrackPublisher = new WorldTrackPublisher();

    @Spy
    ObjectMapper objectMapper = MAPPER;

    @InjectMocks
    WorldService worldService;

    @BeforeEach
    void setUpSeasonStartDate() {
        lenient().when(currentSeasonReader.startDateOf(any())).thenReturn(LocalDate.EPOCH);
        lenient().when(currentSeasonReader.scopeOf(any())).thenReturn(SeasonScope.UNBOUNDED);
    }

    @Test
    @DisplayName("창 규칙을 어기면 참여 여부를 보기 전에 INVALID_REQUEST")
    void sectors_rejects_invalid_window_first() {
        // when & then: 정각이 아닌 창이면 INVALID_REQUEST + 시즌·트랙 조회 없음
        assertErrorCode(() -> worldService.sectors(ME, SECTOR_CODE, DATE, "2026-10-16T13:30:00+09:00", "2026-10-16T14:30:00+09:00"), ErrorCode.INVALID_REQUEST);
        then(currentSeasonReader).shouldHaveNoInteractions();
        then(worldTrackRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이번 시즌 참여 기록이 없으면 WORLD_PARTICIPATION_REQUIRED 이고 트랙을 읽지 않는다")
    void sectors_requires_participation() {
        // given: 이번 시즌 참여 기록이 없다
        given(currentSeasonReader.read()).willReturn(season());
        given(seasonParticipationRepository.findByUserIdAndSeasonId(ME, 1L)).willReturn(Optional.empty());

        // when & then: WORLD_PARTICIPATION_REQUIRED + 트랙 조회 없음
        assertErrorCode(() -> worldService.sectors(ME, SECTOR_CODE, DATE, FROM, TO), ErrorCode.WORLD_PARTICIPATION_REQUIRED);
        then(worldTrackRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("풀이 없거나, 공용·개인 트랙이 모두 없으면 WORLD_TRACK_NOT_FOUND")
    void sectors_without_pool_or_tracks_throws_not_found() {
        // given: 참여자인데 풀이 없다
        givenParticipant(user(ME, null));

        // when & then: 풀 없음은 WORLD_TRACK_NOT_FOUND + 트랙 조회 없음
        assertErrorCode(() -> worldService.sectors(ME, SECTOR_CODE, DATE, FROM, TO), ErrorCode.WORLD_TRACK_NOT_FOUND);
        then(worldTrackRepository).shouldHaveNoInteractions();

        // given: 풀은 있지만 공용·개인 트랙이 모두 없다
        givenParticipant(user(ME, POOL_NUMBER));
        given(worldTrackRepository.findByPoolNumberAndDateAndSectorCodeAndViewerUserId(eq(POOL_NUMBER), eq(DATE), eq(SECTOR_CODE), anyLong()))
                .willReturn(Optional.empty());

        // when & then: WORLD_TRACK_NOT_FOUND
        assertErrorCode(() -> worldService.sectors(ME, SECTOR_CODE, DATE, FROM, TO), ErrorCode.WORLD_TRACK_NOT_FOUND);
    }

    @Test
    @DisplayName("공용 트랙만 있으면 본인·인연은 닉네임, 회차 중 처음 만난 사람·차단(양방향)·탈퇴는 누군가의 트윈으로 붙이고 시각에 +09:00 을 더한다")
    void sectors_names_actors_by_relationship_block_and_withdrawal() {
        // given: 회차 06:00 전 인연(31), 회차 중 처음 만남(32), 내가 차단한 인연(33), 나를 차단한 인연(34), 탈퇴(35)
        User me = user(ME, POOL_NUMBER);
        User withdrawn = user(35L, POOL_NUMBER);
        withdrawn.requestWithdrawal(Duration.ofDays(15));
        givenParticipant(me);
        givenTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, PUBLIC_BODY, slice("sha256:aa", 12, 31, 32, 33, 34, 35));
        givenTrack(ME, null, null);
        given(relationshipRepository.findAllPartnerFirstUpdatesByUserId(eq(ME), any(), any())).willReturn(List.of(
                firstUpdate(31L, LocalDateTime.of(2026, 10, 15, 23, 0)),
                firstUpdate(32L, LocalDateTime.of(2026, 10, 16, 9, 0)),
                firstUpdate(33L, LocalDateTime.of(2026, 10, 10, 9, 0)),
                firstUpdate(34L, LocalDateTime.of(2026, 10, 10, 9, 0))));
        given(blockRepository.findAllByUserId(ME)).willReturn(List.of(Block.create(ME, 33L)));
        given(blockRepository.findAllByUserIdInAndBlockedUserId(any(), eq(ME))).willReturn(List.of(Block.create(34L, ME)));
        given(userRepository.findAllById(any())).willReturn(List.of(
                me, user(31L, POOL_NUMBER), user(32L, POOL_NUMBER), user(33L, POOL_NUMBER), user(34L, POOL_NUMBER), withdrawn));

        // when: 건물 화면 조회
        WorldSectorsResult result = worldService.sectors(ME, SECTOR_CODE, DATE, FROM, TO);

        // then: 맨 위 칸은 트랙·요청 값 그대로, focusRef 는 나
        assertThat(result.poolNumber()).isEqualTo("7");
        assertThat(result.layoutVersion()).isEqualTo("sha256:aa");
        assertThat(result.focusRef()).isEqualTo("user:12");

        // then: 이름·성별 규칙
        assertThat(result.actors()).extracting(actor -> actor.get("displayName").asString())
                .containsExactly("nick12", "nick31", "누군가의 트윈", "누군가의 트윈", "누군가의 트윈", "누군가의 트윈");
        assertThat(result.actors().get(3).get("gender").asString()).isEqualTo("male");
        assertThat(result.actors().get(5).get("gender").isNull()).isTrue();

        // then: 시각 칸에 +09:00 이 붙고, 개인 트랙이 없으니 말 건 엑스트라용 장면은 읽지 않는다
        assertThat(result.actors().get(0).get("segments").get(0).get("start").asString()).isEqualTo("2026-10-16T13:00:00+09:00");
        then(sceneRepository).should(never()).findAllByUserIdAndDateOrderByStartsAtAsc(anyLong(), any());
    }

    @Test
    @DisplayName("공용·개인 트랙이 함께 있으면 내 장면의 서술 줄 speaker 로 말 건 엑스트라를 찾아 개인 트랙에서 더한다")
    void sectors_merges_personal_track_with_speaker_extras() {
        // given: 공용 트랙(나), 개인 트랙(나·말 건 엑스트라·배경 손님), 내 장면에 extra:S1 이 말한 서술 줄
        givenParticipant(user(ME, POOL_NUMBER));
        givenTrack(WorldTrack.PUBLIC_VIEWER_USER_ID, PUBLIC_BODY, slice("sha256:aa", 12));
        WorldTrackSlice personal = slice("sha256:aa", 12);
        personal = withActor(personal, "extra:S1");
        personal = withActor(personal, "extra:BG");
        givenTrack(ME, PERSONAL_BODY, personal);
        given(sceneRepository.findAllByUserIdAndDateOrderByStartsAtAsc(ME, DATE)).willReturn(List.of(Scene.createDialogue(
                ME, DATE, "v1", "카페", "P0103", DATE.atTime(13, 10), DATE.atTime(13, 20), """
                        [{"t": "narr", "text": "손님: \\"여기요\\"", "occursAt": "2026-10-16T13:10:30", "speaker": "extra:S1"}]
                        """)));
        given(userRepository.findAllById(any())).willReturn(List.of(user(ME, POOL_NUMBER)));

        // when: 건물 화면 조회
        WorldSectorsResult result = worldService.sectors(ME, SECTOR_CODE, DATE, FROM, TO);

        // then: 나와 말 건 엑스트라만 남고, 개인 트랙의 배경 손님은 더하지 않는다
        assertThat(result.actors()).extracting(actor -> actor.get("ref").asString()).containsExactlyInAnyOrder("user:12", "extra:S1");
    }

    private void givenParticipant(User viewer) {
        given(currentSeasonReader.read()).willReturn(season());
        given(seasonParticipationRepository.findByUserIdAndSeasonId(ME, 1L)).willReturn(Optional.of(mock(SeasonParticipation.class)));
        given(userRepository.findById(ME)).willReturn(Optional.of(viewer));
    }

    private void givenTrack(long viewerUserId, byte[] body, WorldTrackSlice slice) {
        if (body == null) {
            given(worldTrackRepository.findByPoolNumberAndDateAndSectorCodeAndViewerUserId(POOL_NUMBER, DATE, SECTOR_CODE, viewerUserId))
                    .willReturn(Optional.empty());
            return;
        }

        WorldTrack track = mock(WorldTrack.class);
        given(track.getBody()).willReturn(body);
        given(worldTrackRepository.findByPoolNumberAndDateAndSectorCodeAndViewerUserId(POOL_NUMBER, DATE, SECTOR_CODE, viewerUserId))
                .willReturn(Optional.of(track));
        given(worldTrackWindowReader.read(eq(body), any())).willReturn(slice);
    }

    private static WorldTrackSlice slice(String layoutVersion, long... userIds) {
        List<ObjectNode> actors = new ArrayList<>();
        for (long userId : userIds) {
            actors.add(actor("user:" + userId));
        }
        return new WorldTrackSlice("7", layoutVersion, actors, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static WorldTrackSlice withActor(WorldTrackSlice slice, String ref) {
        List<ObjectNode> actors = new ArrayList<>(slice.actors());
        actors.add(actor(ref));
        return new WorldTrackSlice(slice.poolNumber(), slice.layoutVersion(), actors, slice.talks(), slice.gestures(), slice.objects(), slice.events(), slice.items());
    }

    private static ObjectNode actor(String ref) {
        return (ObjectNode) MAPPER.readTree("""
                {"ref": "%s", "segments": [{"start": "2026-10-16T13:00:00", "end": "2026-10-16T13:30:00"}]}
                """.formatted(ref));
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    private static Season season() {
        Season season = Season.create(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"));
        ReflectionTestUtils.setField(season, "id", 1L);
        return season;
    }

    private static PartnerFirstUpdateProjection firstUpdate(Long partnerUserId, LocalDateTime firstUpdateTime) {
        return new PartnerFirstUpdateProjection() {
            @Override
            public Long getPartnerUserId() {
                return partnerUserId;
            }

            @Override
            public LocalDateTime getFirstUpdateTime() {
                return firstUpdateTime;
            }
        };
    }

    private static User user(Long id, Integer poolNumber) {
        User user = User.create(
                "nick" + id,
                "family", "familyHash",
                "given", "givenHash",
                Gender.MALE,
                "organization", "orgHash",
                "aff", "affHash",
                "20", "affNoHash",
                "2000-01-01", "birthHash",
                "phone" + id, "phoneHash",
                "email" + id + "@test.com", "emailHash", null, null
        , null, null);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "poolNumber", poolNumber);
        return user;
    }
}

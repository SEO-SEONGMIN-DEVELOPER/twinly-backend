package com.nidus.twinly.season.generator;

import com.nidus.twinly.activity.domain.SceneType;
import com.nidus.twinly.activity.entity.ScenePartner;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.activity.repository.SceneRepository.SceneRecapProjection;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository.PairMatchRateProjection;
import com.nidus.twinly.people.repository.TwinViewRepository;
import com.nidus.twinly.people.repository.TwinViewRepository.ViewerCountProjection;
import com.nidus.twinly.relationship.domain.Intimacy;
import com.nidus.twinly.relationship.reader.IntimacyReader;
import com.nidus.twinly.season.domain.SeasonRecapData;
import com.nidus.twinly.season.domain.SeasonRecapPeriod;
import com.nidus.twinly.season.domain.SeasonRecapTimeSlot;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonParticipationRepository.RecapReceiverProjection;
import com.nidus.twinly.season.repository.SeasonRecapRepository;
import com.nidus.twinly.season.repository.SeasonRecapRepository.HourlyActionCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeasonRecapGeneratorUnitTest {

    private static final Long SEASON_ID = 7L;
    private static final SeasonRecapPeriod PERIOD = new SeasonRecapPeriod(
            Instant.parse("2026-09-30T15:00:00Z"), Instant.parse("2026-10-31T15:00:00Z"));
    private static final Long ME = 1L;
    private static final Long OTHER = 2L;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @Mock
    SeasonRecapRepository seasonRecapRepository;

    @Mock
    SceneRepository sceneRepository;

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    TwinViewRepository twinViewRepository;

    @Mock
    BalanceGameAnswerRepository balanceGameAnswerRepository;

    @Mock
    IntimacyReader intimacyReader;

    SeasonRecapGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SeasonRecapGenerator(seasonParticipationRepository, seasonRecapRepository, sceneRepository,
                scenePartnerRepository, twinViewRepository, balanceGameAnswerRepository, intimacyReader,
                JsonMapper.builder().build());
    }

    @Test
    @DisplayName("받는 사람이 없으면 아무 리캡도 만들지 않고 다른 집계를 조회하지 않는다")
    void generate_without_receivers_returns_empty() {
        // given: 대상자 없음
        given(seasonParticipationRepository.findAllRecapReceivers(SEASON_ID, PERIOD.endedAt(), PERIOD.kstStartedAt(), PERIOD.kstEndedAt()))
                .willReturn(List.of());

        // when: 리캡 생성
        Map<Long, SeasonRecapData> result = generator.generate(SEASON_ID, PERIOD);

        // then: 빈 결과 + 장면 조회 없음
        assertThat(result).isEmpty();
        then(sceneRepository).should(never()).findAllRecapByUserIdAndOverlapping(anyLong(), any(), any());
    }

    @Test
    @DisplayName("대상자 전원의 값으로 상위 %를 매기고, 친구·가까운 트윈·밸런스 게임·활동 유형을 항목 정의대로 계산한다")
    void generate_computes_items_and_ranks_against_population() {
        // given: 대상자 2명 — 나(1)는 10-01 이전 참여, 다른 사람(2)은 10-05 참여
        given(seasonParticipationRepository.findAllRecapReceivers(SEASON_ID, PERIOD.endedAt(), PERIOD.kstStartedAt(), PERIOD.kstEndedAt()))
                .willReturn(List.of(receiver(ME, Instant.parse("2026-09-20T00:00:00Z")), receiver(OTHER, Instant.parse("2026-10-05T01:00:00Z"))));

        // given: 나는 10-02 대화(20·30번과, 내 말풍선 2개 + 상대 1개 + 기간 밖 1개) + 10-10 행동(30번과)
        String lines = """
                [{"t":"narr","text":"시작","occursAt":"2026-10-02T10:00:00"},
                 {"t":"bubble","userId":1,"action":null,"text":"안녕","occursAt":"2026-10-02T10:01:00"},
                 {"t":"bubble","userId":20,"action":null,"text":"응","occursAt":"2026-10-02T10:02:00"},
                 {"t":"bubble","userId":1,"action":null,"text":"뭐해","occursAt":"2026-10-02T10:03:00"},
                 {"t":"bubble","userId":1,"action":null,"text":"밖","occursAt":"2026-11-02T10:03:00"}]
                """;
        given(sceneRepository.findAllRecapByUserIdAndOverlapping(ME, PERIOD.kstStartedAt(), PERIOD.kstEndedAt())).willReturn(List.of(
                scene(100L, SceneType.DIALOGUE, "2026-10-02T10:00", "2026-10-02T10:30", lines),
                scene(101L, SceneType.ACTION, "2026-10-10T09:00", "2026-10-10T10:00", null)));
        given(scenePartnerRepository.findAllBySceneIdIn(List.of(100L, 101L))).willReturn(List.of(
                ScenePartner.create(100L, 20L), ScenePartner.create(100L, 30L), ScenePartner.create(100L, ME),
                ScenePartner.create(101L, 30L)));

        // given: 다른 사람은 행동 120분만
        given(sceneRepository.findAllRecapByUserIdAndOverlapping(OTHER, PERIOD.kstStartedAt(), PERIOD.kstEndedAt())).willReturn(List.of(
                scene(200L, SceneType.ACTION, "2026-10-06T09:00", "2026-10-06T11:00", null)));
        given(scenePartnerRepository.findAllBySceneIdIn(List.of(200L))).willReturn(List.of());

        // given: 집계 끝 친밀도 — 20번은 40(게임 10), 30번은 34(게임 0)
        given(intimacyReader.readAllAsOf(eq(ME), anyList(), eq(PERIOD.endedAt())))
                .willReturn(Map.of(20L, new Intimacy(40, 10), 30L, new Intimacy(34, 0)));
        given(intimacyReader.readAllAsOf(eq(OTHER), anyList(), eq(PERIOD.endedAt()))).willReturn(Map.of());

        // given: 프로필 조회자(나 5명), 행동 시각(저녁 8·심야 3), 밸런스 게임(20번 3회 중 2번, 30번 2회)
        given(twinViewRepository.countDistinctProfileViewersByViewedAtRange(PERIOD.startedAt(), PERIOD.endedAt()))
                .willReturn(List.of(viewerCount(ME, 5)));
        given(seasonRecapRepository.countActionsByUserIdAndKstHour(PERIOD.startedAt(), PERIOD.endedAt()))
                .willReturn(List.of(hourly(ME, 19, 8), hourly(ME, 23, 3)));
        given(balanceGameAnswerRepository.countMatchRatesBySeasonIdAndRoundStartsAtRange(SEASON_ID, PERIOD.startedAt(), PERIOD.endedAt()))
                .willReturn(List.of(matchRate(ME, 20L, 3, 2), matchRate(ME, 30L, 2, 2)));

        // when: 리캡 생성
        Map<Long, SeasonRecapData> result = generator.generate(SEASON_ID, PERIOD);

        // then: 두 사람 모두 같은 기간·인원
        SeasonRecapData mine = result.get(ME);
        assertThat(result).hasSize(2);
        assertThat(mine.population()).isEqualTo(2);
        assertThat(mine.startedAt()).isEqualTo(PERIOD.startedAt());
        assertThat(result.get(OTHER).population()).isEqualTo(2);

        // then: 함께한 날 — 시즌 시작(10-01)부터 마지막 장면 끝(10-10)까지 10일, 다른 사람은 10-05부터 10-06까지 2일
        assertThat(mine.togetherDays()).isEqualTo(10);
        assertThat(result.get(OTHER).togetherDays()).isEqualTo(2);

        // then: 활동 시간과 상위 % — 대화 30분 1등(50%), 행동 60분은 120분보다 작아 100%, 이동 0분은 null
        assertThat(mine.dialogueMinutes()).isEqualTo(new SeasonRecapData.Ranked(30, 50));
        assertThat(mine.actionMinutes()).isEqualTo(new SeasonRecapData.Ranked(60, 100));
        assertThat(result.get(OTHER).actionMinutes()).isEqualTo(new SeasonRecapData.Ranked(120, 50));
        assertThat(mine.moveMinutes()).isEqualTo(new SeasonRecapData.Ranked(0, null));

        // then: 내 말풍선은 기간 안의 내 것 2개, 대화한 사람은 나 자신을 뺀 2명, 친구는 친밀도 35 이상인 1명
        assertThat(mine.bubbles()).isEqualTo(new SeasonRecapData.Ranked(2, 50));
        assertThat(mine.talked()).isEqualTo(new SeasonRecapData.Ranked(2, 50));
        assertThat(mine.friends()).isEqualTo(new SeasonRecapData.Ranked(1, 50));

        // then: 많이 만난 상대는 30번(2일) → 20번(1일), 가까운 트윈은 게임 몫을 뺀 34·30 순
        assertThat(mine.mostMet()).containsExactly(
                new SeasonRecapData.MostMet(30L, 2, 2),
                new SeasonRecapData.MostMet(20L, 1, 1));
        assertThat(mine.closest()).containsExactly(
                new SeasonRecapData.Closest(30L, 34),
                new SeasonRecapData.Closest(20L, 30));

        // then: 밸런스 게임은 서로 답한 회차 3번 이상만, 일치율은 소수 첫째 자리
        assertThat(mine.bestMatches()).containsExactly(new SeasonRecapData.BestMatch(20L, 66.7, 3, 2));

        // then: 프로필 조회자 5명(상위 50%), 다른 사람은 0명이라 null
        assertThat(mine.profileViewers()).isEqualTo(new SeasonRecapData.Ranked(5, 50));
        assertThat(result.get(OTHER).profileViewers()).isEqualTo(new SeasonRecapData.Ranked(0, null));

        // then: 활동 유형은 저녁, 다른 사람은 행동이 없어 null
        assertThat(mine.activityType()).isEqualTo(new SeasonRecapData.ActivityType(SeasonRecapTimeSlot.EVENING, 0, 0, 8, 3));
        assertThat(result.get(OTHER).activityType()).isNull();
    }

    @Test
    @DisplayName("대사 JSON이 깨진 장면은 말풍선 수에서만 빼고 나머지 집계는 계속한다")
    void generate_skips_broken_lines() {
        // given: 대상자 1명, 대사가 깨진 대화 장면 1개
        given(seasonParticipationRepository.findAllRecapReceivers(SEASON_ID, PERIOD.endedAt(), PERIOD.kstStartedAt(), PERIOD.kstEndedAt()))
                .willReturn(List.of(receiver(ME, PERIOD.startedAt())));
        given(sceneRepository.findAllRecapByUserIdAndOverlapping(ME, PERIOD.kstStartedAt(), PERIOD.kstEndedAt()))
                .willReturn(List.of(scene(100L, SceneType.DIALOGUE, "2026-10-02T10:00", "2026-10-02T10:30", "{broken")));
        given(scenePartnerRepository.findAllBySceneIdIn(List.of(100L))).willReturn(List.of());
        given(intimacyReader.readAllAsOf(eq(ME), anyList(), eq(PERIOD.endedAt()))).willReturn(Map.of());

        // when: 리캡 생성
        SeasonRecapData mine = generator.generate(SEASON_ID, PERIOD).get(ME);

        // then: 말풍선 0, 대화 시간은 그대로 30분
        assertThat(mine.bubbles().value()).isZero();
        assertThat(mine.dialogueMinutes().value()).isEqualTo(30);
    }

    private RecapReceiverProjection receiver(Long userId, Instant participatedInAt) {
        return new RecapReceiverProjection() {
            public Long getUserId() { return userId; }
            public Instant getParticipatedInAt() { return participatedInAt; }
        };
    }

    private SceneRecapProjection scene(Long id, SceneType type, String startsAt, String endsAt, String lines) {
        return new SceneRecapProjection() {
            public Long getId() { return id; }
            public SceneType getType() { return type; }
            public LocalDateTime getStartsAt() { return LocalDateTime.parse(startsAt); }
            public LocalDateTime getEndsAt() { return LocalDateTime.parse(endsAt); }
            public String getPlace() { return "카페"; }
            public String getPlaceCode() { return "P0123"; }
            public String getLines() { return lines; }
        };
    }

    private ViewerCountProjection viewerCount(Long userId, long count) {
        return new ViewerCountProjection() {
            public Long getTargetUserId() { return userId; }
            public Long getViewerCount() { return count; }
        };
    }

    private HourlyActionCountProjection hourly(Long userId, long hour, long count) {
        return new HourlyActionCountProjection() {
            public Long getUserId() { return userId; }
            public Long getKstHour() { return hour; }
            public Long getCount() { return count; }
        };
    }

    private PairMatchRateProjection matchRate(Long userId, Long partnerUserId, long compared, long matched) {
        return new PairMatchRateProjection() {
            public Long getUserId() { return userId; }
            public Long getPartnerUserId() { return partnerUserId; }
            public Long getComparedCount() { return compared; }
            public Long getMatchedCount() { return matched; }
        };
    }
}

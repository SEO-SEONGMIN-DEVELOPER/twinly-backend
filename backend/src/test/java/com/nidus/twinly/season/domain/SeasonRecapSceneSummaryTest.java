package com.nidus.twinly.season.domain;

import com.nidus.twinly.activity.domain.SceneType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonRecapSceneSummaryTest {

    private static final SeasonRecapPeriod PERIOD = new SeasonRecapPeriod(
            Instant.parse("2026-09-30T15:00:00Z"), Instant.parse("2026-10-31T15:00:00Z"));

    private long sceneId = 0;

    @Test
    @DisplayName("장면 종류별 시간을 분으로 더하고 기간 밖 장면은 세지 않는다")
    void minutes_by_type_within_period() {
        // given: 대화 30분, 행동 90분, 이동 10분, 기간이 끝난 뒤의 행동 60분
        List<SeasonRecapScene> scenes = List.of(
                scene(SceneType.DIALOGUE, "2026-10-02T10:00", "2026-10-02T10:30", "카페", "P0123", Set.of(2L), 4),
                scene(SceneType.ACTION, "2026-10-02T11:00", "2026-10-02T12:30", "카페", "P0123", Set.of(), 0),
                scene(SceneType.MOVE, "2026-10-02T12:30", "2026-10-02T12:40", "공원", "P0200", Set.of(), 0),
                scene(SceneType.ACTION, "2026-11-01T01:00", "2026-11-01T02:00", "공원", "P0200", Set.of(), 0));

        // when: 요약
        SeasonRecapSceneSummary summary = SeasonRecapSceneSummary.of(scenes, PERIOD);

        // then: 종류별 분·말풍선·대화 상대
        assertThat(summary.dialogueMinutes()).isEqualTo(30);
        assertThat(summary.actionMinutes()).isEqualTo(90);
        assertThat(summary.moveMinutes()).isEqualTo(10);
        assertThat(summary.bubbles()).isEqualTo(4);
        assertThat(summary.talkedPartnerUserIds()).containsExactly(2L);
        assertThat(summary.lastEndsAt()).isEqualTo(LocalDateTime.parse("2026-10-02T12:40"));
    }

    @Test
    @DisplayName("이동을 빼고 이어진 같은 장소는 1번 방문이며 집은 빼고 방문 수·머문 시간 순으로 TOP 3를 준다")
    void places_merge_consecutive_and_exclude_home() {
        // given: 카페 → 이동 → 카페(같은 방문) → 집 → 카페(새 방문) → 도서관 → 공원 → 서점
        List<SeasonRecapScene> scenes = List.of(
                scene(SceneType.ACTION, "2026-10-02T10:00", "2026-10-02T10:30", "카페", "P0123", Set.of(), 0),
                scene(SceneType.MOVE, "2026-10-02T10:30", "2026-10-02T10:40", "카페", "P0123", Set.of(), 0),
                scene(SceneType.ACTION, "2026-10-02T10:40", "2026-10-02T11:00", "학생 거리 카페", "P0123", Set.of(), 0),
                scene(SceneType.ACTION, "2026-10-02T12:00", "2026-10-02T15:00", "주택가 집", "P0258", Set.of(), 0),
                scene(SceneType.ACTION, "2026-10-03T10:00", "2026-10-03T10:10", "카페", "P0123", Set.of(), 0),
                scene(SceneType.ACTION, "2026-10-03T11:00", "2026-10-03T13:00", "도서관", "P0300", Set.of(), 0),
                scene(SceneType.ACTION, "2026-10-03T14:00", "2026-10-03T14:30", "공원", null, Set.of(), 0),
                scene(SceneType.ACTION, "2026-10-03T15:00", "2026-10-03T15:10", "서점", "P0400", Set.of(), 0));

        // when: 요약
        List<SeasonRecapData.Place> places = SeasonRecapSceneSummary.of(scenes, PERIOD).places();

        // then: 카페 2회(50+10분, 이름은 마지막 방문) → 도서관 1회 120분 → 공원 1회 30분(코드 없음), 집·서점은 빠짐
        assertThat(places).containsExactly(
                new SeasonRecapData.Place("P0123", "카페", 2, 60),
                new SeasonRecapData.Place("P0300", "도서관", 1, 120),
                new SeasonRecapData.Place(null, "공원", 1, 30));
    }

    @Test
    @DisplayName("함께한 날은 같은 날 여러 번이어도 1일이고, 날 수·장면 수 순으로 TOP 3를 준다")
    void mostMet_counts_distinct_days() {
        // given: 2번은 같은 날 2장면 + 다른 날 1장면, 3번은 2일 2장면, 4번은 1일 3장면
        List<SeasonRecapScene> scenes = List.of(
                scene(SceneType.DIALOGUE, "2026-10-02T10:00", "2026-10-02T10:30", "카페", "P0123", Set.of(2L, 3L), 0),
                scene(SceneType.ACTION, "2026-10-02T11:00", "2026-10-02T11:30", "카페", "P0123", Set.of(2L, 4L), 0),
                scene(SceneType.ACTION, "2026-10-02T12:00", "2026-10-02T12:30", "카페", "P0123", Set.of(4L), 0),
                scene(SceneType.ACTION, "2026-10-02T13:00", "2026-10-02T13:30", "카페", "P0123", Set.of(4L), 0),
                scene(SceneType.ACTION, "2026-10-03T10:00", "2026-10-03T10:30", "카페", "P0123", Set.of(2L, 3L), 0));

        // when: 요약
        SeasonRecapSceneSummary summary = SeasonRecapSceneSummary.of(scenes, PERIOD);

        // then: 2번(2일 3장면) → 3번(2일 2장면) → 4번(1일 3장면), 대화한 상대는 2·3번
        assertThat(summary.mostMet()).containsExactly(
                new SeasonRecapData.MostMet(2L, 2, 3),
                new SeasonRecapData.MostMet(3L, 2, 2),
                new SeasonRecapData.MostMet(4L, 1, 3));
        assertThat(summary.talkedPartnerUserIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    private SeasonRecapScene scene(SceneType type, String startsAt, String endsAt, String place, String placeCode,
                                   Set<Long> partnerUserIds, int myBubbleCount) {
        return new SeasonRecapScene(++sceneId, type, LocalDateTime.parse(startsAt), LocalDateTime.parse(endsAt),
                place, placeCode, partnerUserIds, myBubbleCount);
    }
}

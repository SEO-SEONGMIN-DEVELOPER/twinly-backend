package com.nidus.twinly.season.domain;

import com.nidus.twinly.season.entity.Season;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonRecapPeriodTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-30T15:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-10-31T14:59:59Z");

    @Test
    @DisplayName("예정보다 일찍 리셋하면 집계 끝은 리셋 시각이다")
    void of_early_reset_ends_at_reset() {
        // given: 예정 종료 하루 전에 다음 시즌이 만들어짐
        Instant resetAt = Instant.parse("2026-10-30T15:00:00Z");

        // when: 집계 기간 계산
        SeasonRecapPeriod period = SeasonRecapPeriod.of(season(STARTED_AT, ENDED_AT, null), season(resetAt, resetAt.plusSeconds(1), resetAt));

        // then: 끝은 리셋 시각
        assertThat(period.startedAt()).isEqualTo(STARTED_AT);
        assertThat(period.endedAt()).isEqualTo(resetAt);
    }

    @Test
    @DisplayName("예정보다 늦게 리셋하면 집계 끝은 예정 종료 시각이다")
    void of_late_reset_ends_at_scheduled_end() {
        // given: 예정 종료 이틀 뒤에 다음 시즌이 만들어짐
        Instant resetAt = ENDED_AT.plusSeconds(2 * 86400);

        // when: 집계 기간 계산
        SeasonRecapPeriod period = SeasonRecapPeriod.of(season(STARTED_AT, ENDED_AT, null), season(resetAt, resetAt.plusSeconds(1), resetAt));

        // then: 끝은 예정 종료 시각
        assertThat(period.endedAt()).isEqualTo(ENDED_AT);
    }

    @Test
    @DisplayName("기간에 걸친 장면은 기간 안에 든 부분만 초로 센다")
    void overlapSeconds_clips_to_period() {
        // given: KST 10-01 00:00 ~ 11-01 00:00 기간
        SeasonRecapPeriod period = new SeasonRecapPeriod(STARTED_AT, Instant.parse("2026-10-31T15:00:00Z"));

        // when & then: 시작 전 30분부터 시작 후 30분까지면 30분, 기간 밖이면 0
        assertThat(period.overlapSeconds(LocalDateTime.parse("2026-09-30T23:30:00"), LocalDateTime.parse("2026-10-01T00:30:00"))).isEqualTo(1800);
        assertThat(period.overlapSeconds(LocalDateTime.parse("2026-11-01T01:00:00"), LocalDateTime.parse("2026-11-01T02:00:00"))).isZero();
        assertThat(period.overlaps(LocalDateTime.parse("2026-11-01T00:00:00"), LocalDateTime.parse("2026-11-01T01:00:00"))).isFalse();
    }

    private Season season(Instant startedAt, Instant endedAt, Instant createdAt) {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "startedAt", startedAt);
        ReflectionTestUtils.setField(season, "endedAt", endedAt);
        ReflectionTestUtils.setField(season, "activatedAt", createdAt);
        return season;
    }
}

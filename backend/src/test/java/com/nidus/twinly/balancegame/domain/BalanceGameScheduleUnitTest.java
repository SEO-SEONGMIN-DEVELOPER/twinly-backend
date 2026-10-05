package com.nidus.twinly.balancegame.domain;

import com.nidus.twinly.common.time.KstTimes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceGameScheduleUnitTest {

    @ParameterizedTest(name = "KST {0} 에는 {1} 질문")
    @CsvSource({
            "2026-10-03T00:00:00, 2026-10-02T18:00:00",
            "2026-10-03T11:59:59, 2026-10-02T18:00:00",
            "2026-10-03T12:00:00, 2026-10-03T12:00:00",
            "2026-10-03T17:59:59, 2026-10-03T12:00:00",
            "2026-10-03T18:00:00, 2026-10-03T18:00:00",
            "2026-10-03T23:59:59, 2026-10-03T18:00:00"
    })
    @DisplayName("질문은 KST 12시·18시에 나오고, 다음 질문이 나올 때까지 이어진다")
    void roundStartOf_returns_latest_release(String kstNow, String expectedKstStart) {
        // when & then
        assertThat(BalanceGameSchedule.roundStartOf(LocalDateTime.parse(kstNow))).isEqualTo(kst(expectedKstStart));
    }

    @Test
    @DisplayName("12시 질문은 같은 날 18시에, 18시 질문은 다음 날 12시에 끝난다")
    void nextRoundStartOf_is_next_release() {
        // when & then
        assertThat(BalanceGameSchedule.nextRoundStartOf(kst("2026-10-03T12:00:00"))).isEqualTo(kst("2026-10-03T18:00:00"));
        assertThat(BalanceGameSchedule.nextRoundStartOf(kst("2026-10-03T18:00:00"))).isEqualTo(kst("2026-10-04T12:00:00"));
    }

    @Test
    @DisplayName("방금 끝난 회차는 지금 회차의 바로 앞 회차다")
    void previousRoundStartOf_is_round_just_ended() {
        // when & then: 12시 10초에는 어제 18시 회차, 18시 10초에는 오늘 12시 회차
        assertThat(BalanceGameSchedule.previousRoundStartOf(LocalDateTime.parse("2026-10-03T12:00:10")))
                .isEqualTo(kst("2026-10-02T18:00:00"));
        assertThat(BalanceGameSchedule.previousRoundStartOf(LocalDateTime.parse("2026-10-03T18:00:10")))
                .isEqualTo(kst("2026-10-03T12:00:00"));
    }

    @Test
    @DisplayName("이어지는 회차는 날짜가 바뀌어도 순번이 1씩 늘어난다 (질문이 건너뛰지 않고 차례로 나온다)")
    void sequenceOf_increases_by_one_per_round() {
        // given: 12시 → 18시 → 다음 날 12시
        long noon = BalanceGameSchedule.sequenceOf(kst("2026-10-03T12:00:00"));
        long evening = BalanceGameSchedule.sequenceOf(kst("2026-10-03T18:00:00"));
        long nextNoon = BalanceGameSchedule.sequenceOf(kst("2026-10-04T12:00:00"));

        // when & then
        assertThat(evening - noon).isEqualTo(1);
        assertThat(nextNoon - evening).isEqualTo(1);
    }

    private Instant kst(String localDateTime) {
        return KstTimes.toInstant(LocalDateTime.parse(localDateTime));
    }
}

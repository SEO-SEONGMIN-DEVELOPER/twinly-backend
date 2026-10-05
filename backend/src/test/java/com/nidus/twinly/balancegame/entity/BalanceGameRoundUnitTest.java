package com.nidus.twinly.balancegame.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceGameRoundUnitTest {

    @Test
    @DisplayName("12시 회차는 18시 정각에 닫힌다")
    void noon_round_ends_at_six_pm() {
        // given: KST 12시 회차
        BalanceGameRound round = round(Instant.parse("2026-10-03T03:00:00Z"));

        // when & then
        assertThat(round.endsAt()).isEqualTo(Instant.parse("2026-10-03T09:00:00Z"));
        assertThat(round.isEnded(Instant.parse("2026-10-03T08:59:59.999999Z"))).isFalse();
        assertThat(round.isEnded(Instant.parse("2026-10-03T09:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("18시 회차는 새벽을 지나 다음 날 12시 정각에 닫힌다")
    void evening_round_ends_at_next_noon() {
        // given: KST 18시 회차
        BalanceGameRound round = round(Instant.parse("2026-10-03T09:00:00Z"));

        // when & then
        assertThat(round.endsAt()).isEqualTo(Instant.parse("2026-10-04T03:00:00Z"));
        assertThat(round.isEnded(Instant.parse("2026-10-03T20:00:00Z"))).isFalse();
    }

    private BalanceGameRound round(Instant startsAt) {
        BalanceGameRound round = BeanUtils.instantiateClass(BalanceGameRound.class);
        ReflectionTestUtils.setField(round, "startsAt", startsAt);
        return round;
    }
}

package com.nidus.twinly.balancegame.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceGameRoundUnitTest {

    @Test
    @DisplayName("회차는 시작부터 한 시간 동안 열려 있고, 끝 시각 정각부터 닫힌다")
    void round_is_open_for_an_hour() {
        // given: 10시(KST) 회차
        BalanceGameRound round = BeanUtils.instantiateClass(BalanceGameRound.class);
        ReflectionTestUtils.setField(round, "startsAt", Instant.parse("2026-10-03T01:00:00Z"));

        // when & then
        assertThat(round.endsAt()).isEqualTo(Instant.parse("2026-10-03T02:00:00Z"));
        assertThat(round.isEnded(Instant.parse("2026-10-03T01:59:59.999999Z"))).isFalse();
        assertThat(round.isEnded(Instant.parse("2026-10-03T02:00:00Z"))).isTrue();
    }
}

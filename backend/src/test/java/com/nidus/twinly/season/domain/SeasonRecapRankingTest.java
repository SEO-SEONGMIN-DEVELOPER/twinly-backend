package com.nidus.twinly.season.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonRecapRankingTest {

    @Test
    @DisplayName("나보다 큰 사람 수에 1을 더해 인원으로 나눈 백분율을 올림한다")
    void topPercentOf_rounds_up() {
        // given: 4명의 값 30, 20, 20, 0
        SeasonRecapRanking ranking = SeasonRecapRanking.of(List.of(30, 20, 20, 0));

        // when & then: 30은 상위 25%, 20은 동점이라 둘 다 상위 50%
        assertThat(ranking.topPercentOf(30)).isEqualTo(25);
        assertThat(ranking.topPercentOf(20)).isEqualTo(50);
    }

    @Test
    @DisplayName("나누어떨어지지 않으면 올림한다")
    void topPercentOf_ceil_when_not_divisible() {
        // given: 3명 중 1등
        SeasonRecapRanking ranking = SeasonRecapRanking.of(List.of(9, 5, 1));

        // when & then: 1/3 = 33.3..% → 34, 3/3 = 100
        assertThat(ranking.topPercentOf(9)).isEqualTo(34);
        assertThat(ranking.topPercentOf(1)).isEqualTo(100);
    }

    @Test
    @DisplayName("값이 0이면 상위 %를 주지 않는다")
    void topPercentOf_zero_is_null() {
        // given: 0이 섞인 값들
        SeasonRecapRanking ranking = SeasonRecapRanking.of(List.of(3, 0, 0));

        // when & then: 0은 null
        assertThat(ranking.topPercentOf(0)).isNull();
    }
}

package com.nidus.twinly.relationship.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntimacyUnitTest {

    @Test
    @DisplayName("최종 친밀도는 시뮬레이션 친밀도에 기준 시각 이후 게임 점수를 더하고, 게임 몫은 지금까지 얻은 게임 점수 전부다")
    void of_adds_bonus_after_as_of_and_keeps_total_as_game() {
        // given: AI가 기준 시각 전 게임 점수 5가 반영된 40을 줬고, 그 뒤 6을 더 얻었다

        // when
        Intimacy intimacy = Intimacy.of(40, 6, 11);

        // then: 40 + 6, 게임 몫은 5 + 6
        assertThat(intimacy).isEqualTo(new Intimacy(46, 11));
    }

    @Test
    @DisplayName("최종 친밀도는 100을 넘지 않는다")
    void of_caps_value_at_100() {
        // when: 95에 10을 더한다
        Intimacy intimacy = Intimacy.of(95, 10, 10);

        // then: 100에서 멈춘다
        assertThat(intimacy).isEqualTo(new Intimacy(100, 10));
    }

    @Test
    @DisplayName("시뮬레이션이 친밀도를 깎아 게임 점수 합보다 낮아지면, 게임 몫은 최종 친밀도를 넘지 않는다")
    void of_caps_game_at_value() {
        // given: 게임으로 30을 얻었지만 AI가 그 뒤 친밀도를 20으로 깎았다

        // when
        Intimacy intimacy = Intimacy.of(20, 0, 30);

        // then: 게이지 전체가 20이므로 게임 몫도 20, 시뮬레이션 몫은 0
        assertThat(intimacy).isEqualTo(new Intimacy(20, 20));
    }
}

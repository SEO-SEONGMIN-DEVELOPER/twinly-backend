package com.nidus.twinly.common.parallel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MonotoneCubicCurveUnitTest {

    private static final List<double[]> KNOTS = List.of(
            new double[]{0.0, 10}, new double[]{0.10, 30}, new double[]{0.29, 50},
            new double[]{0.51, 70}, new double[]{0.90, 85}, new double[]{1.0, 99});

    @Test
    @DisplayName("꺾은점에서는 설정한 값을 정확히 지난다")
    void passes_through_knots() {
        // given: 운영 설정과 같은 꺾은점
        MonotoneCubicCurve curve = MonotoneCubicCurve.through(KNOTS);

        // when & then: 모든 꺾은점에서 y 값이 그대로 나온다
        for (double[] knot : KNOTS) {
            assertThat(curve.valueAt(knot[0])).isCloseTo(knot[1], within(1e-9));
        }
    }

    @Test
    @DisplayName("꺾은점 사이에서도 값이 줄어들지 않는다")
    void never_decreases_between_knots() {
        // given: 운영 설정과 같은 꺾은점
        MonotoneCubicCurve curve = MonotoneCubicCurve.through(KNOTS);

        // when: 0부터 1까지 촘촘히 훑는다
        double previous = curve.valueAt(0.0);
        for (int i = 1; i <= 10_000; i++) {
            double value = curve.valueAt(i / 10_000.0);

            // then: 앞 값보다 작아지는 지점이 없다
            assertThat(value).isGreaterThanOrEqualTo(previous);
            previous = value;
        }
    }

    @Test
    @DisplayName("직선 보간과 달리 꺾은점 사이가 매끄럽게 휘어진다")
    void bends_between_knots() {
        // given: 운영 설정과 같은 꺾은점
        MonotoneCubicCurve curve = MonotoneCubicCurve.through(KNOTS);

        // when: 29%와 51% 사이 한가운데를 본다
        double value = curve.valueAt(0.40);

        // then: 직선 보간값 60과 다르면서 양 끝 사이에 있다
        assertThat(value).isNotCloseTo(60.0, within(0.01));
        assertThat(value).isBetween(50.0, 70.0);
    }

    @Test
    @DisplayName("정의역 밖의 값은 양 끝 값으로 고정된다")
    void clamps_outside_domain() {
        // given: 운영 설정과 같은 꺾은점
        MonotoneCubicCurve curve = MonotoneCubicCurve.through(KNOTS);

        // when & then: 0 아래와 1 위는 각각 첫 값과 마지막 값이다
        assertThat(curve.valueAt(-0.5)).isEqualTo(10);
        assertThat(curve.valueAt(1.5)).isEqualTo(99);
    }

    @Test
    @DisplayName("오름차순이 아닌 점이 있으면 만들 수 없다")
    void rejects_unordered_points() {
        // given: y 가 거꾸로 가는 점
        List<double[]> unordered = List.of(new double[]{0.0, 10}, new double[]{0.5, 70}, new double[]{1.0, 50});

        // when & then: 생성 시점에 터진다
        assertThatThrownBy(() -> MonotoneCubicCurve.through(unordered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("(1.0, 50.0)");
    }

    @Test
    @DisplayName("역함수는 꺾은점의 값을 넣으면 그 자리의 누적 비율을 돌려준다")
    void inverse_recovers_knot_percentile() {
        // given: 운영 설정과 같은 꺾은점
        MonotoneCubicCurve curve = MonotoneCubicCurve.through(KNOTS);

        // when & then: 모든 꺾은점에서 원래의 누적 비율이 나온다
        for (double[] knot : KNOTS) {
            assertThat(curve.inverseAt(knot[1])).isCloseTo(knot[0], within(1e-9));
        }
    }

    @Test
    @DisplayName("곡선 값을 역함수에 되넣으면 원래 누적 비율로 돌아온다")
    void inverse_round_trips() {
        // given: 운영 설정과 같은 꺾은점
        MonotoneCubicCurve curve = MonotoneCubicCurve.through(KNOTS);

        // when & then: 0부터 1까지 훑어도 왕복 오차가 없다
        for (int i = 0; i <= 100; i++) {
            double percentile = i / 100.0;

            assertThat(curve.inverseAt(curve.valueAt(percentile))).isCloseTo(percentile, within(1e-9));
        }
    }

    @Test
    @DisplayName("치역 밖의 값은 양 끝 누적 비율로 고정된다")
    void inverse_clamps_outside_range() {
        // given: 운영 설정과 같은 꺾은점
        MonotoneCubicCurve curve = MonotoneCubicCurve.through(KNOTS);

        // when & then: 최저 점수 아래는 0, 최고 점수 위는 1이다
        assertThat(curve.inverseAt(9.5)).isEqualTo(0.0);
        assertThat(curve.inverseAt(120.0)).isEqualTo(1.0);
    }
}

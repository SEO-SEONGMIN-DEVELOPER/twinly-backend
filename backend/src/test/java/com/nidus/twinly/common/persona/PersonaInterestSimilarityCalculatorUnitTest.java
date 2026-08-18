package com.nidus.twinly.common.persona;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class PersonaInterestSimilarityCalculatorUnitTest {

    PersonaInterestSimilarityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PersonaInterestSimilarityCalculator();
    }

    @Test
    @DisplayName("겹치는 관심사 수를 두 집합 크기의 기하평균으로 나눈다")
    void similarity_is_ochiai_coefficient() {
        // given: 4개와 2개 중 2개가 겹친다
        List<String> interests = List.of("등산", "재즈", "영화", "요리");
        List<String> otherInterests = List.of("등산", "재즈");

        // when
        OptionalDouble similarity = calculator.similarity(interests, otherInterests);

        // then: 2 / sqrt(4 * 2)
        assertThat(similarity).hasValueCloseTo(0.7071067811, offset(1e-9));
    }

    @Test
    @DisplayName("관심사를 많이 적은 유저가 구조적으로 불리해지지 않는다")
    void similarity_tolerates_size_imbalance() {
        // given: 20개와 3개 중 3개가 모두 겹친다
        List<String> interests = IntStream.range(0, 20).mapToObj(i -> "관심사" + i).toList();
        List<String> otherInterests = List.of("관심사0", "관심사1", "관심사2");

        // when
        OptionalDouble similarity = calculator.similarity(interests, otherInterests);

        // then: 3 / sqrt(20 * 3) = 0.387, 자카드(3/20 = 0.15)보다 완화된다
        assertThat(similarity).hasValueCloseTo(0.3872983346, offset(1e-9));
    }

    @Test
    @DisplayName("표기가 흔들려도 같은 관심사로 센다")
    void similarity_normalizes_notation() {
        // given: 공백, 대소문자, 전각 문자만 다르다
        List<String> interests = List.of("재즈 ", "Jazz Piano", "ｸﾞﾚｰ");
        List<String> otherInterests = List.of("재즈", "jazzpiano", "グレー");

        // when
        OptionalDouble similarity = calculator.similarity(interests, otherInterests);

        // then: 셋 다 같은 값으로 정규화되어 완전 일치한다
        assertThat(similarity).hasValueCloseTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("같은 관심사를 여러 번 적어도 한 번만 센다")
    void duplicated_interests_are_counted_once() {
        // given: 중복 입력과 공백만 다른 입력이 섞여 있다
        List<String> interests = List.of("등산", "등산", "등 산");
        List<String> otherInterests = List.of("등산");

        // when
        OptionalDouble similarity = calculator.similarity(interests, otherInterests);

        // then: 중복을 세면 1.0을 넘거나 값이 흔들리므로 1.0이어야 한다
        assertThat(similarity).hasValueCloseTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("겹치는 관심사가 없으면 0점이다")
    void no_shared_interest_scores_zero() {
        // given
        List<String> interests = List.of("등산", "재즈");
        List<String> otherInterests = List.of("게임", "낚시");

        // when
        OptionalDouble similarity = calculator.similarity(interests, otherInterests);

        // then: 비교는 성립했으므로 값이 존재하되 0이다
        assertThat(similarity).hasValueCloseTo(0.0, offset(1e-9));
    }

    @Test
    @DisplayName("한쪽이라도 관심사가 없으면 0점이 아니라 산출 불가다")
    void empty_interests_are_not_scorable() {
        // given: 빈 목록과 공백만 입력한 목록
        List<String> interests = List.of("등산", "재즈");

        // when & then
        assertThat(calculator.similarity(interests, List.of())).isEmpty();
        assertThat(calculator.similarity(List.of(), interests)).isEmpty();
        assertThat(calculator.similarity(interests, List.of("   "))).isEmpty();
    }

    @Test
    @DisplayName("순서를 바꿔 계산해도 같은 점수가 나온다")
    void similarity_is_symmetric() {
        // given
        List<String> interests = List.of("등산", "재즈", "영화");
        List<String> otherInterests = List.of("재즈", "게임");

        // when & then
        assertThat(calculator.similarity(interests, otherInterests))
                .isEqualTo(calculator.similarity(otherInterests, interests));
    }
}

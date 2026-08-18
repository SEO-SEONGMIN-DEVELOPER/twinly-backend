package com.nidus.twinly.common.persona;

import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyTraitRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class PersonaSurveySimilarityCalculatorUnitTest {

    PersonaSurveySimilarityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PersonaSurveySimilarityCalculator();
    }

    @Test
    @DisplayName("차원마다 문항 수가 달라도 각 차원은 자기 문항 안에서만 일치율을 낸다")
    void dimensionScores_are_computed_within_each_dimension() {
        // given: EXTRAVERSION 4문항 중 3개 일치, CONSCIENTIOUSNESS 2문항 중 1개 일치
        Map<Integer, SurveyTraitRef> answers = answers(Map.of(
                1, ref(1, PersonaDimension.EXTRAVERSION, SurveyOptionName.A),
                2, ref(2, PersonaDimension.EXTRAVERSION, SurveyOptionName.A),
                3, ref(3, PersonaDimension.EXTRAVERSION, SurveyOptionName.A),
                4, ref(4, PersonaDimension.EXTRAVERSION, SurveyOptionName.A),
                5, ref(5, PersonaDimension.CONSCIENTIOUSNESS, SurveyOptionName.A),
                6, ref(6, PersonaDimension.CONSCIENTIOUSNESS, SurveyOptionName.A)
        ));
        Map<Integer, SurveyTraitRef> otherAnswers = answers(Map.of(
                1, ref(1, PersonaDimension.EXTRAVERSION, SurveyOptionName.A),
                2, ref(2, PersonaDimension.EXTRAVERSION, SurveyOptionName.A),
                3, ref(3, PersonaDimension.EXTRAVERSION, SurveyOptionName.A),
                4, ref(4, PersonaDimension.EXTRAVERSION, SurveyOptionName.B),
                5, ref(5, PersonaDimension.CONSCIENTIOUSNESS, SurveyOptionName.A),
                6, ref(6, PersonaDimension.CONSCIENTIOUSNESS, SurveyOptionName.B)
        ));

        // when
        Map<PersonaDimension, Double> scores = calculator.dimensionScores(answers, otherAnswers);

        // then: 문항 수가 많은 차원이 다른 차원의 점수를 끌어당기지 않는다
        assertThat(scores.get(PersonaDimension.EXTRAVERSION)).isCloseTo(0.75, offset(1e-9));
        assertThat(scores.get(PersonaDimension.CONSCIENTIOUSNESS)).isCloseTo(0.5, offset(1e-9));
    }

    @Test
    @DisplayName("한쪽만 답한 문항은 분자에서도 분모에서도 제외된다")
    void questions_answered_by_only_one_side_are_excluded() {
        // given: 2번 문항은 상대에게 없다
        Map<Integer, SurveyTraitRef> answers = answers(Map.of(
                1, ref(1, PersonaDimension.OPENNESS, SurveyOptionName.A),
                2, ref(2, PersonaDimension.OPENNESS, SurveyOptionName.B)
        ));
        Map<Integer, SurveyTraitRef> otherAnswers = answers(Map.of(
                1, ref(1, PersonaDimension.OPENNESS, SurveyOptionName.A)
        ));

        // when
        Map<PersonaDimension, Double> scores = calculator.dimensionScores(answers, otherAnswers);

        // then: 공통 1문항만 비교하므로 1.0이다 (2번을 불일치로 세면 0.5가 되어 실패한다)
        assertThat(scores.get(PersonaDimension.OPENNESS)).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("공통 문항이 없는 차원은 0점이 아니라 결과에서 빠진다")
    void dimension_without_common_question_is_absent() {
        // given: 같은 차원이지만 겹치는 문항이 하나도 없다
        Map<Integer, SurveyTraitRef> answers = answers(Map.of(
                1, ref(1, PersonaDimension.NEUROTICISM, SurveyOptionName.A)
        ));
        Map<Integer, SurveyTraitRef> otherAnswers = answers(Map.of(
                2, ref(2, PersonaDimension.NEUROTICISM, SurveyOptionName.A)
        ));

        // when
        Map<PersonaDimension, Double> scores = calculator.dimensionScores(answers, otherAnswers);

        // then: 산출 불가와 0점을 구분한다
        assertThat(scores).doesNotContainKey(PersonaDimension.NEUROTICISM);
        assertThat(scores).isEmpty();
    }

    @Test
    @DisplayName("모두 다르게 답하면 0점이 된다")
    void all_different_answers_score_zero() {
        // given
        Map<Integer, SurveyTraitRef> answers = answers(Map.of(
                1, ref(1, PersonaDimension.LIFE_STYLE, SurveyOptionName.A),
                2, ref(2, PersonaDimension.LIFE_STYLE, SurveyOptionName.A)
        ));
        Map<Integer, SurveyTraitRef> otherAnswers = answers(Map.of(
                1, ref(1, PersonaDimension.LIFE_STYLE, SurveyOptionName.B),
                2, ref(2, PersonaDimension.LIFE_STYLE, SurveyOptionName.B)
        ));

        // when
        Map<PersonaDimension, Double> scores = calculator.dimensionScores(answers, otherAnswers);

        // then: 비교는 됐으므로 결과에 존재하되 0점이다
        assertThat(scores.get(PersonaDimension.LIFE_STYLE)).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    @DisplayName("순서를 바꿔 계산해도 같은 점수가 나온다")
    void dimensionScores_are_symmetric() {
        // given
        Map<Integer, SurveyTraitRef> answers = answers(Map.of(
                1, ref(1, PersonaDimension.CONFLICT_STYLE, SurveyOptionName.A),
                2, ref(2, PersonaDimension.CONFLICT_STYLE, SurveyOptionName.B),
                3, ref(3, PersonaDimension.CONFLICT_STYLE, SurveyOptionName.A)
        ));
        Map<Integer, SurveyTraitRef> otherAnswers = answers(Map.of(
                1, ref(1, PersonaDimension.CONFLICT_STYLE, SurveyOptionName.A),
                2, ref(2, PersonaDimension.CONFLICT_STYLE, SurveyOptionName.A),
                3, ref(3, PersonaDimension.CONFLICT_STYLE, SurveyOptionName.A)
        ));

        // when & then
        assertThat(calculator.dimensionScores(answers, otherAnswers))
                .isEqualTo(calculator.dimensionScores(otherAnswers, answers));
    }

    private Map<Integer, SurveyTraitRef> answers(Map<Integer, SurveyTraitRef> refs) {
        return new LinkedHashMap<>(refs);
    }

    private SurveyTraitRef ref(Integer questionId, PersonaDimension dimension, SurveyOptionName optionName) {
        return new SurveyTraitRef(questionId, dimension, optionName);
    }
}

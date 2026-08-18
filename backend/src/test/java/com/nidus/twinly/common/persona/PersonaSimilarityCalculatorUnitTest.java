package com.nidus.twinly.common.persona;

import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class PersonaSimilarityCalculatorUnitTest {

    SurveyLoader surveyLoader;
    PersonaSimilarityCalculator calculator;

    @BeforeEach
    void setUp() throws IOException {
        // given: 실제 설문 파일과 실제 협력 객체로 조립한다
        surveyLoader = new SurveyLoader();
        ReflectionTestUtils.setField(surveyLoader, "objectMapper", new ObjectMapper());
        surveyLoader.load();

        calculator = new PersonaSimilarityCalculator(
                new PersonaSurveyAnswerResolver(surveyLoader),
                new PersonaSurveySimilarityCalculator(),
                new PersonaInterestSimilarityCalculator()
        );
    }

    @Test
    @DisplayName("설문과 관심사가 모두 같으면 1.0이다")
    void identical_persona_scores_one() {
        // given: 모든 문항에 A를 고르고 관심사도 같은 두 유저
        Map<PersonaDimension, List<String>> elements = elements(question -> SurveyOptionName.A, List.of("등산", "재즈"));
        Map<PersonaDimension, List<String>> otherElements = elements(question -> SurveyOptionName.A, List.of("재즈", "등산"));

        // when
        PersonaSimilarity similarity = calculator.similarity(elements, otherElements);

        // then: 설문 8개 차원과 관심사까지 9개 항목이 모두 만점이다
        assertThat(similarity.score()).isCloseTo(1.0, offset(1e-9));
        assertThat(similarity.dimensionScores()).hasSize(9);
        assertThat(similarity.dimensionScores()).containsKey(PersonaDimension.INTEREST);
    }

    @Test
    @DisplayName("최종 점수는 차원별 점수의 평균이다")
    void score_is_the_average_of_dimension_scores() {
        // given: 일부 문항만 다르게 답한 두 유저
        Map<PersonaDimension, List<String>> elements = elements(question -> SurveyOptionName.A, List.of("등산"));
        Map<PersonaDimension, List<String>> otherElements = elements(
                question -> question.id() % 2 == 0 ? SurveyOptionName.A : SurveyOptionName.B,
                List.of("등산", "재즈")
        );

        // when
        PersonaSimilarity similarity = calculator.similarity(elements, otherElements);

        // then: 내역을 그대로 평균한 값과 일치한다
        double expected = similarity.dimensionScores().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();

        assertThat(similarity.score()).isCloseTo(expected, offset(1e-9));
    }

    @Test
    @DisplayName("관심사를 적지 않은 유저는 관심사 항목이 분모에서 빠진다")
    void missing_interests_are_excluded_from_the_average() {
        // given: 설문 답은 완전히 같지만 한쪽만 관심사를 적었다
        Map<PersonaDimension, List<String>> elements = elements(question -> SurveyOptionName.A, List.of("등산", "재즈"));
        Map<PersonaDimension, List<String>> otherElements = elements(question -> SurveyOptionName.A, List.of());

        // when
        PersonaSimilarity similarity = calculator.similarity(elements, otherElements);

        // then: 관심사를 0점으로 세면 0.89가 되지만, 분모에서 빼므로 1.0이다
        assertThat(similarity.dimensionScores()).doesNotContainKey(PersonaDimension.INTEREST);
        assertThat(similarity.dimensionScores()).hasSize(8);
        assertThat(similarity.score()).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("문항 수가 많은 차원이 최종 점수를 더 많이 끌어내리지 않는다")
    void dimension_with_more_questions_does_not_dominate() {
        // given: 문항이 4개인 차원만 전부 틀린 경우와, 문항이 2개인 차원만 전부 틀린 경우
        Map<PersonaDimension, List<String>> elements = elements(question -> SurveyOptionName.A, List.of());

        PersonaSimilarity extraversionFlipped = calculator.similarity(
                elements, elements(flipped(PersonaDimension.EXTRAVERSION), List.of()));
        PersonaSimilarity conscientiousnessFlipped = calculator.similarity(
                elements, elements(flipped(PersonaDimension.CONSCIENTIOUSNESS), List.of()));

        // then: 문항 수(4개 vs 2개)와 무관하게 8개 차원 중 하나가 0점이 되어 동일하다
        assertThat(extraversionFlipped.score()).isCloseTo(7.0 / 8.0, offset(1e-9));
        assertThat(conscientiousnessFlipped.score()).isCloseTo(extraversionFlipped.score(), offset(1e-9));
    }

    @Test
    @DisplayName("대화 요약(DETAIL)은 점수에 영향을 주지 않는다")
    void detail_elements_are_ignored() {
        // given: 설문과 관심사는 같고 DETAIL만 완전히 다른 두 유저
        Map<PersonaDimension, List<String>> elements = elements(question -> SurveyOptionName.A, List.of("등산"));
        elements.put(PersonaDimension.DETAIL, List.of("주말엔 뭐 해?: 등산 가요"));

        Map<PersonaDimension, List<String>> otherElements = elements(question -> SurveyOptionName.A, List.of("등산"));
        otherElements.put(PersonaDimension.DETAIL, List.of("요즘 관심사가 뭐야?: 딱히 없어요"));

        // when
        PersonaSimilarity similarity = calculator.similarity(elements, otherElements);

        // then: DETAIL은 어느 항목에도 반영되지 않는다
        assertThat(similarity.dimensionScores()).doesNotContainKey(PersonaDimension.DETAIL);
        assertThat(similarity.score()).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("순서를 바꿔 계산해도 같은 점수가 나온다")
    void similarity_is_symmetric() {
        // given
        Map<PersonaDimension, List<String>> elements = elements(question -> SurveyOptionName.A, List.of("등산", "재즈"));
        Map<PersonaDimension, List<String>> otherElements = elements(
                question -> question.id() % 3 == 0 ? SurveyOptionName.B : SurveyOptionName.A,
                List.of("재즈", "게임")
        );

        // when & then
        assertThat(calculator.similarity(elements, otherElements))
                .isEqualTo(calculator.similarity(otherElements, elements));
    }

    private Function<SurveyQuestion, SurveyOptionName> flipped(PersonaDimension target) {
        return question -> question.dimension() == target ? SurveyOptionName.B : SurveyOptionName.A;
    }

    private Map<PersonaDimension, List<String>> elements(Function<SurveyQuestion, SurveyOptionName> chooser, List<String> interests) {
        Map<PersonaDimension, List<String>> elements = new EnumMap<>(PersonaDimension.class);

        for (SurveyQuestion question : surveyLoader.getAllQuestions()) {
            elements.computeIfAbsent(question.dimension(), dimension -> new ArrayList<>())
                    .add(question.traitFor(chooser.apply(question)));
        }

        if (!interests.isEmpty()) {
            elements.put(PersonaDimension.INTEREST, interests);
        }

        return elements;
    }
}

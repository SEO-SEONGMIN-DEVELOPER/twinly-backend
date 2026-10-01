package com.nidus.twinly.common.persona;

import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityTypeCalculatorUnitTest {

    SurveyLoader surveyLoader;
    PersonalityTypeCalculator calculator;

    @BeforeEach
    void setUp() throws IOException {
        // given: 실제 설문 파일을 그대로 로드한다 (문항별 높은 쪽 선택지가 이 테스트의 전제)
        surveyLoader = new SurveyLoader();
        ReflectionTestUtils.setField(surveyLoader, "objectMapper", new ObjectMapper());
        surveyLoader.load();

        calculator = new PersonalityTypeCalculator(surveyLoader, new PersonaSurveyAnswerResolver(surveyLoader));
        calculator.init();
    }

    @Test
    @DisplayName("모든 문항에 A 로 답하면 9번이 B 가 높은 쪽이어도 외향성은 5문항 중 4개가 높은 쪽이라 11111 이다")
    void calculate_all_a() {
        // given: 31문항 모두 A
        List<String> explanations = traits(Map.of());

        // when & then
        assertThat(calculator.calculate(explanations)).contains("11111");
    }

    @Test
    @DisplayName("외향성 9번은 B 가 높은 쪽이라, 7·8·9번 A 와 10·11번 B 면 높은 쪽은 2개뿐이어서 차분(0)이다")
    void calculate_respects_reversed_question() {
        // given: 외향성 10·11번만 B, 나머지는 모두 A
        List<String> explanations = traits(Map.of(
                10, SurveyOptionName.B,
                11, SurveyOptionName.B
        ));

        // when & then: A 를 무조건 높은 쪽으로 보면 7·8·9번 3개가 높은 쪽이 되어 1이 나온다
        assertThat(calculator.calculate(explanations)).contains("01111");
    }

    @Test
    @DisplayName("Big5 문항 하나라도 답이 없으면 동점이 생길 수 있어 유형을 정하지 않는다")
    void calculate_empty_when_big_five_answer_missing() {
        // given: 신경성 15번 답만 빠짐
        List<String> explanations = new ArrayList<>(traits(Map.of()));
        explanations.remove(surveyLoader.getQuestion(15).traitFor(SurveyOptionName.A));

        // when & then
        assertThat(calculator.calculate(explanations)).isEmpty();
    }

    @Test
    @DisplayName("Big5 가 아닌 문항의 답이 빠져도 유형은 정해진다")
    void calculate_ignores_non_big_five_answers() {
        // given: lifeStyle 18번 답만 빠짐
        List<String> explanations = new ArrayList<>(traits(Map.of()));
        explanations.remove(surveyLoader.getQuestion(18).traitFor(SurveyOptionName.A));

        // when & then
        assertThat(calculator.calculate(explanations)).contains("11111");
    }

    private List<String> traits(Map<Integer, SurveyOptionName> answers) {
        return surveyLoader.getAllQuestions().stream()
                .map(question -> question.traitFor(answers.getOrDefault(question.id(), SurveyOptionName.A)))
                .toList();
    }
}

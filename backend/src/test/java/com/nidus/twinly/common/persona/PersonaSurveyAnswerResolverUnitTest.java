package com.nidus.twinly.common.persona;

import com.nidus.twinly.common.survey.SurveyLoader;
import com.nidus.twinly.common.survey.SurveyOptionName;
import com.nidus.twinly.common.survey.SurveyQuestion;
import com.nidus.twinly.common.survey.SurveyTraitRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaSurveyAnswerResolverUnitTest {

    SurveyLoader surveyLoader;
    PersonaSurveyAnswerResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        // given: 실제 설문 파일을 그대로 로드한다
        surveyLoader = new SurveyLoader();
        ReflectionTestUtils.setField(surveyLoader, "objectMapper", new ObjectMapper());
        surveyLoader.load();

        resolver = new PersonaSurveyAnswerResolver(surveyLoader);
    }

    @Test
    @DisplayName("설문에서 온 원소들이 문항별 답안으로 환원된다")
    void resolve_restores_answers_by_question() {
        // given: 모든 문항에 A를 선택한 유저의 persona element 설명들
        List<SurveyQuestion> questions = surveyLoader.getAllQuestions();
        List<String> explanations = questions.stream()
                .map(question -> question.traitFor(SurveyOptionName.A))
                .toList();

        // when: 문항별 답안으로 환원한다
        Map<Integer, SurveyTraitRef> answers = resolver.resolve(explanations);

        // then: 문항 수만큼 복원되고 모두 A로 기록된다
        assertThat(answers).hasSize(questions.size());
        assertThat(answers.values()).allSatisfy(ref -> assertThat(ref.optionName()).isEqualTo(SurveyOptionName.A));
        assertThat(answers.keySet()).containsExactlyInAnyOrderElementsOf(questions.stream().map(SurveyQuestion::id).toList());
    }

    @Test
    @DisplayName("관심사와 대화 요약처럼 설문에서 오지 않은 원소는 조용히 제외된다")
    void resolve_ignores_non_survey_elements() {
        // given: 설문 원소 하나에 관심사와 DETAIL 원소가 섞인 목록
        SurveyQuestion question = surveyLoader.getAllQuestions().get(0);
        List<String> explanations = List.of(
                "등산",
                question.traitFor(SurveyOptionName.B),
                "주말엔 보통 뭐 해?: 집에서 영화 봐요"
        );

        // when: 문항별 답안으로 환원한다
        Map<Integer, SurveyTraitRef> answers = resolver.resolve(explanations);

        // then: 설문에서 온 원소 하나만 남는다
        assertThat(answers).hasSize(1);
        assertThat(answers.get(question.id()).optionName()).isEqualTo(SurveyOptionName.B);
    }

    @Test
    @DisplayName("같은 문항의 답이 중복되면 나중에 저장된 답이 남는다")
    void resolve_keeps_latest_answer_for_duplicated_question() {
        // given: 같은 문항에 대해 A 다음 B가 저장된 목록 (id 오름차순 조회 결과를 가정)
        SurveyQuestion question = surveyLoader.getAllQuestions().get(0);
        List<String> explanations = List.of(
                question.traitFor(SurveyOptionName.A),
                question.traitFor(SurveyOptionName.B)
        );

        // when: 문항별 답안으로 환원한다
        Map<Integer, SurveyTraitRef> answers = resolver.resolve(explanations);

        // then: 최신 답인 B만 남는다
        assertThat(answers).hasSize(1);
        assertThat(answers.get(question.id()).optionName()).isEqualTo(SurveyOptionName.B);
    }
}

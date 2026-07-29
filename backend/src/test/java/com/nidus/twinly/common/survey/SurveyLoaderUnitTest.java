package com.nidus.twinly.common.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SurveyLoaderUnitTest {

    SurveyLoader surveyLoader;

    @BeforeEach
    void setUp() throws IOException {
        // given: 실제 설문 파일을 그대로 로드한다 (문항 순서와 id 순서가 다른 것이 이 테스트의 전제)
        surveyLoader = new SurveyLoader();
        surveyLoader.objectMapper = new ObjectMapper();
        surveyLoader.load();
    }

    @Test
    @DisplayName("마지막 문항은 id 순서가 아니라 파일에 실린 노출 순서상 마지막 문항이다")
    void isLastQuestion_follows_file_order_not_id_order() {
        // given: 파일 순서상 마지막 문항
        List<SurveyQuestion> questions = surveyLoader.getAllQuestions();
        Integer lastInFile = questions.get(questions.size() - 1).id();

        // when & then: 노출 순서상 마지막 문항만 마지막으로 판정된다
        assertThat(surveyLoader.isLastQuestion(lastInFile)).isTrue();

        // then: 문항 개수를 마지막 문항 id로 쓰던 과거 결함이 되살아나면 실패한다
        assertThat(surveyLoader.isLastQuestion(questions.size())).isFalse();
    }

    @Test
    @DisplayName("파일 순서상 마지막이 아닌 문항은 마지막으로 판정되지 않는다")
    void isLastQuestion_false_for_non_last_questions() {
        // given: 마지막을 제외한 모든 문항
        List<SurveyQuestion> questions = surveyLoader.getAllQuestions();

        // when & then: 어느 것도 마지막으로 판정되지 않는다
        assertThat(questions.subList(0, questions.size() - 1))
                .allSatisfy(question -> assertThat(surveyLoader.isLastQuestion(question.id())).isFalse());
    }
}

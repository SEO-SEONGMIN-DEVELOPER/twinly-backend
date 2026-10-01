package com.nidus.twinly.common.tendency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TendencyLoaderUnitTest {

    ObjectMapper objectMapper = new ObjectMapper();

    TendencyLoader tendencyLoader;

    @BeforeEach
    void setUp() throws IOException {
        // given: 실제 성향 문항 파일을 그대로 로드한다
        tendencyLoader = new TendencyLoader(objectMapper);
        tendencyLoader.load();
    }

    @Test
    @DisplayName("문항은 id 크기순이 아니라 파일에 실린 순서대로 내려간다")
    void getAllQuestions_follows_file_order() throws IOException {
        // given: 파일에 적힌 순서 그대로의 문항 id
        JsonNode root = objectMapper.readTree(new ClassPathResource("tendency/tendency_v1.json").getInputStream());
        List<Long> idsInFile = new ArrayList<>();
        root.get("questions").forEach(questionNode -> idsInFile.add(questionNode.get("id").asLong()));

        // when: 전체 문항 조회
        List<TendencyQuestion> questions = tendencyLoader.getAllQuestions();

        // then: 파일 순서와 같은 순서로 전 문항이 담김
        assertThat(questions).extracting(TendencyQuestion::id).containsExactlyElementsOf(idsInFile);
    }

    @Test
    @DisplayName("파일에 실린 모든 문항은 id로 다시 찾을 수 있다")
    void findQuestion_returns_every_loaded_question() {
        // given: 파일에 실린 모든 문항
        List<TendencyQuestion> questions = tendencyLoader.getAllQuestions();

        // when & then: 어떤 문항이든 자기 id로 조회하면 같은 문항이 나온다
        assertThat(questions).allSatisfy(question ->
                assertThat(tendencyLoader.findQuestion(question.id())).contains(question));
    }

    @Test
    @DisplayName("파일에 없는 문항 id로 찾으면 비어 있다")
    void findQuestion_with_unknown_id_is_empty() {
        // given: 파일의 어떤 문항 id보다 큰 id
        Long unknownId = tendencyLoader.getAllQuestions().stream()
                .mapToLong(TendencyQuestion::id)
                .max()
                .orElseThrow() + 1;

        // when & then: 빈 결과
        assertThat(tendencyLoader.findQuestion(unknownId)).isEmpty();
    }

    @Test
    @DisplayName("문항은 자기 선택지 id만 가진 것으로 판정하고, 없는 선택지 id는 거른다")
    void hasOption_accepts_only_own_options() {
        // given: 파일에 실린 모든 문항
        List<TendencyQuestion> questions = tendencyLoader.getAllQuestions();

        // when & then: 자기 선택지는 모두 통과하고, 가장 큰 선택지 id보다 큰 값은 통과하지 못한다
        assertThat(questions).allSatisfy(question -> {
            assertThat(question.options()).allSatisfy(option ->
                    assertThat(question.hasOption(option.id())).isTrue());

            Long unknownOptionId = question.options().stream()
                    .mapToLong(TendencyOption::id)
                    .max()
                    .orElseThrow() + 1;
            assertThat(question.hasOption(unknownOptionId)).isFalse();
        });
    }
}

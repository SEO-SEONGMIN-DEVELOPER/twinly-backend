package com.nidus.twinly.common.balancegame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceGameQuestionLoaderUnitTest {

    private static final Instant STARTS_AT = Instant.parse("2026-10-03T01:00:00Z");

    private BalanceGameQuestionLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        loader = new BalanceGameQuestionLoader(new ObjectMapper());
        loader.load();
    }

    @Test
    @DisplayName("실제 질문 파일을 읽고, 질문마다 선택지가 정확히 두 개다")
    void loads_questions_with_two_options() {
        // when & then: 검증에 걸리면 load 에서 예외가 나므로, 읽힌 질문을 꺼내 모양만 확인한다
        BalanceGameQuestion question = loader.findQuestion(1L).orElseThrow();
        assertThat(question.options()).hasSize(2);
        assertThat(question.hasOption(1L)).isTrue();
        assertThat(question.hasOption(3L)).isFalse();
        assertThat(loader.findQuestion(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("같은 시간이면 서버·요청과 관계없이 모두에게 같은 질문이 나온다")
    void questionFor_is_deterministic() {
        // when & then
        assertThat(loader.questionFor(STARTS_AT)).isEqualTo(loader.questionFor(STARTS_AT));
    }

    @Test
    @DisplayName("하루 24시간 동안 같은 질문이 두 번 나오지 않는다")
    void questionFor_does_not_repeat_within_a_day() {
        // when: 24시간치 질문
        long distinct = IntStream.range(0, 24)
                .mapToObj(hour -> loader.questionFor(STARTS_AT.plus(Duration.ofHours(hour))).id())
                .distinct()
                .count();

        // then
        assertThat(distinct).isEqualTo(24);
    }
}

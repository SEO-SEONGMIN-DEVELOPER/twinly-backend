package com.nidus.twinly.simulation.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationPreloadRequestUnitTest {

    @Test
    @DisplayName("AI 서버와 약속한 형식으로 직렬화된다: userId 는 문자열, 시각은 오프셋 없는 초 단위, 날짜는 YYYY-MM-DD")
    void serializes_in_agreed_shape() {
        // given
        SimulationPreloadRequest request = new SimulationPreloadRequest(
                12L,
                LocalDateTime.of(2026, 9, 3, 1, 5, 9),
                List.of(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4)));

        // when
        String json = JsonMapper.builder().build().writeValueAsString(request);

        // then
        assertThat(json).isEqualTo(
                "{\"userId\":\"12\",\"grantedAt\":\"2026-09-03T01:05:09\",\"dates\":[\"2026-09-03\",\"2026-09-04\"]}");
    }
}

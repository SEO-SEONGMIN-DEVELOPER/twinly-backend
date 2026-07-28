package com.nidus.twinly.anon.integration;

import com.jayway.jsonpath.JsonPath;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnonIntegrationTest extends AbstractIntegrationTest {

    private static final Duration TTL = Duration.ofDays(14);

    @Autowired
    AnonSessionRepository anonSessionRepository;

    @Test
    @DisplayName("익명 세션 시작: 인증 없이 호출해도 200과 토큰을 응답하고 실제 DB에 세션 행이 생성된다")
    void start_success_end_to_end() throws Exception {
        // given: 만료시각이 발급 시점 + 14일인지 판정하기 위해 호출 직전 시각을 기록
        Instant before = Instant.now();

        // when: 인증 헤더 없이 익명 세션 시작 API 호출 (인증이 필요 없는 공개 엔드포인트)
        String body = mockMvc.perform(post("/api/v1/anon/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonSessionToken").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andReturn().getResponse().getContentAsString();

        // then: 응답 토큰으로 실제 DB에서 세션을 찾을 수 있고, 만료시각은 발급 시점 + 14일이며 온보딩 필드는 비어 있다
        UUID token = UUID.fromString(JsonPath.read(body, "$.anonSessionToken"));
        AnonSession saved = anonSessionRepository.findByToken(token).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getExpiresAt())
                .isBetween(before.plus(TTL).minusSeconds(1), Instant.now().plus(TTL).plusSeconds(1));
        assertThat(saved.getNickname()).isNull();
        assertThat(saved.getGender()).isNull();
    }

    @Test
    @DisplayName("익명 세션 시작을 두 번 호출하면 서로 다른 토큰의 세션이 각각 DB에 저장된다")
    void start_twice_creates_two_distinct_sessions() throws Exception {
        // given: 호출 전 익명 세션 행 수를 기록
        long beforeCount = anonSessionRepository.count();

        // when: 익명 세션 시작 API를 연속으로 두 번 호출
        String firstBody = mockMvc.perform(post("/api/v1/anon/start"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondBody = mockMvc.perform(post("/api/v1/anon/start"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then: 서로 다른 토큰이 발급되고 DB의 익명 세션 행이 2건 늘어난다 (token 유니크 제약 통과)
        String firstToken = JsonPath.read(firstBody, "$.anonSessionToken");
        String secondToken = JsonPath.read(secondBody, "$.anonSessionToken");

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(anonSessionRepository.findByToken(UUID.fromString(firstToken))).isPresent();
        assertThat(anonSessionRepository.findByToken(UUID.fromString(secondToken))).isPresent();
        assertThat(anonSessionRepository.count()).isEqualTo(beforeCount + 2);
    }
}

package com.nidus.twinly.connection.integration;

import com.jayway.jsonpath.JsonPath;
import com.nidus.twinly.connection.domain.ConnectionType;
import com.nidus.twinly.connection.entity.ConnectionTicket;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConnectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ConnectionTicketRepository connectionTicketRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("WS 연결 토큰 발급 성공: 실제 유저·JWT 인증·MockMvc·DB까지 관통하여 connection_tickets 행이 생성된다")
    void token_ws_success_end_to_end() throws Exception {
        // given: 실제 유저 저장 (connection_tickets.user_id가 users를 FK로 참조)
        User me = saveUser();

        // when: 해당 유저의 실제 액세스 토큰으로 WS 연결 토큰 발급 API 호출
        String response = mockMvc.perform(post("/api/v1/connection-tokens")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionType\":\"WS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty())
                .andExpect(jsonPath("$.connectionType").value("WS"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: 응답의 티켓 UUID로 DB에서 미사용 티켓 행이 실제로 조회되고 TTL은 60초다
        UUID ticket = UUID.fromString(JsonPath.read(response, "$.ticket"));
        ConnectionTicket saved = connectionTicketRepository.findByTicket(ticket).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(me.getId());
        assertThat(saved.getConnectionType()).isEqualTo(ConnectionType.WS);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(Duration.between(saved.getCreatedAt(), saved.getExpiresAt()))
                .isBetween(Duration.ofSeconds(59), Duration.ofSeconds(61));
    }

    @Test
    @DisplayName("SSE 연결 토큰 발급 성공: 요청한 connectionType이 응답과 DB 행에 그대로 반영된다")
    void token_sse_success_end_to_end() throws Exception {
        // given: 실제 유저 저장
        User me = saveUser();

        // when: 해당 유저의 실제 액세스 토큰으로 SSE 연결 토큰 발급 API 호출
        String response = mockMvc.perform(post("/api/v1/connection-tokens")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionType\":\"SSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionType").value("SSE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: DB 행의 connectionType도 SSE로 저장됨
        UUID ticket = UUID.fromString(JsonPath.read(response, "$.ticket"));
        ConnectionTicket saved = connectionTicketRepository.findByTicket(ticket).orElseThrow();
        assertThat(saved.getConnectionType()).isEqualTo(ConnectionType.SSE);
    }

    @Test
    @DisplayName("만료 시각은 DB 세션 타임존이 UTC가 아니어도 UTC 기준으로 저장되어 TTL 60초가 유지된다")
    void expiresAt_is_stored_in_utc_regardless_of_db_session_time_zone() throws Exception {
        // given: 만료 시각은 앱이 Instant로 계산해 저장하고, consume은 DB의 UTC_TIMESTAMP()와 비교한다.
        //        두 시계 기준이 어긋나면 TTL 60초가 9시간이 되거나 발급 즉시 만료된다.
        //        JVM(Asia/Seoul)과 다른 DB 세션 타임존을 강제해 그 어긋남이 실제로 생기는지 고정한다.
        User me = saveUser();
        entityManager.createNativeQuery("SET time_zone = '+09:00'").executeUpdate();

        // when: 연결 토큰 발급
        String response = mockMvc.perform(post("/api/v1/connection-tokens")
                        .header("Authorization", bearer(me.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionType\":\"WS\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: DB가 보는 expires_at과 UTC_TIMESTAMP()의 차이가 TTL과 같다 (9시간 밀리면 32460초가 된다)
        UUID ticket = UUID.fromString(JsonPath.read(response, "$.ticket"));
        entityManager.flush();
        Number ttlSeconds = (Number) entityManager.createNativeQuery("""
                        SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(6), expires_at)
                        FROM connection_tickets WHERE ticket = :ticket
                        """)
                .setParameter("ticket", ticket)
                .getSingleResult();
        assertThat(ttlSeconds.longValue()).isBetween(55L, 60L);

        // then: 같은 기준으로 판정하는 consume도 정상 동작한다
        assertThat(connectionTicketRepository.consume(ticket)).isEqualTo(1);
    }

    @Test
    @DisplayName("유효하지 않은 액세스 토큰이면 401을 반환하고 티켓 행이 생성되지 않는다")
    void token_with_invalid_access_token_returns_401() throws Exception {
        // given: 발급 전 티켓 행 수를 기록
        long before = connectionTicketRepository.count();

        // when: 위조된 액세스 토큰으로 연결 토큰 발급 API 호출
        mockMvc.perform(post("/api/v1/connection-tokens")
                        .header("Authorization", "Bearer invalid-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionType\":\"WS\"}"))
                .andExpect(status().isUnauthorized());

        // then: 티켓 행이 늘어나지 않음
        assertThat(connectionTicketRepository.count()).isEqualTo(before);
    }
}

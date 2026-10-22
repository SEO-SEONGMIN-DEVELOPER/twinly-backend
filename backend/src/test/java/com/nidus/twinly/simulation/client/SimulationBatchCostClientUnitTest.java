package com.nidus.twinly.simulation.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.simulation.config.AiServerProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationBatchCostClientUnitTest {

    private static final String PATH = "/internal/v1/admin/batch-costs";

    HttpServer server;
    SimulationBatchCostClient client;
    int status;
    String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PATH, exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        AiServerProperties properties = new AiServerProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofSeconds(3));
        client = new SimulationBatchCostClient(JsonMapper.builder().build(), properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("200 응답의 금액·세계 시간을 그대로 읽고, 배치를 안 돌린 그룹의 null 은 0 으로 바꾸지 않는다")
    void fetch_success() {
        // given: 1번 그룹은 오늘 배치 진행 중, 2번 그룹은 배치를 한 번도 안 돌린 상태
        status = 200;
        responseBody = """
                {"simulationDate":"2026-10-22","groupCount":2,"startedGroupCount":1,"participantCount":32,
                 "totalCostUsd":1.402331,"averageCostUsd":0.043823,"unpricedResponseCount":0,
                 "groups":[
                  {"groupNumber":1,"worldTime":"2026-10-22T14:35:00","simulationDate":"2026-10-22","participantCount":32,
                   "totalCostUsd":1.402331,"averageCostUsd":0.043823,"unpricedResponseCount":0},
                  {"groupNumber":2,"worldTime":"2026-10-22T09:00:00","simulationDate":null,"participantCount":null,
                   "totalCostUsd":null,"averageCostUsd":null,"unpricedResponseCount":null}
                 ]}
                """;

        // when
        SimulationBatchCostBody body = client.fetch();

        // then
        assertThat(body.simulationDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(body.startedGroupCount()).isEqualTo(1);
        assertThat(body.totalCostUsd()).isEqualByComparingTo(new BigDecimal("1.402331"));
        assertThat(body.groups()).hasSize(2);
        assertThat(body.groups().getFirst().worldTime()).isEqualTo(LocalDateTime.of(2026, 10, 22, 14, 35));
        assertThat(body.groups().getFirst().averageCostUsd()).isEqualByComparingTo(new BigDecimal("0.043823"));
        SimulationBatchCostBody.Group notStarted = body.groups().get(1);
        assertThat(notStarted.simulationDate()).isNull();
        assertThat(notStarted.participantCount()).isNull();
        assertThat(notStarted.totalCostUsd()).isNull();
        assertThat(notStarted.unpricedResponseCount()).isNull();
    }

    @Test
    @DisplayName("AI 서버가 503 을 주면 AI 비용 조회 실패로 바꾼다")
    void fetch_serviceUnavailable() {
        // given: AI DB 를 읽지 못한 AI 서버
        status = 503;
        responseBody = "";

        // when & then
        assertThatThrownBy(() -> client.fetch())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIMULATION_BATCH_COST_FETCH_FAILED);
    }

    @Test
    @DisplayName("200 인데 본문이 비어 있으면 AI 비용 조회 실패로 바꾼다")
    void fetch_emptyBody() {
        // given
        status = 200;
        responseBody = "";

        // when & then
        assertThatThrownBy(() -> client.fetch())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIMULATION_BATCH_COST_FETCH_FAILED);
    }

    @Test
    @DisplayName("AI 서버에 연결하지 못하면 AI 비용 조회 실패로 바꾼다")
    void fetch_connectionRefused() {
        // given: 닫힌 포트
        server.stop(0);

        // when & then
        assertThatThrownBy(() -> client.fetch())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIMULATION_BATCH_COST_FETCH_FAILED);
    }
}

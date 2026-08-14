package com.nidus.twinly.connection.integration;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketControlBody;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import com.nidus.twinly.support.AbstractWebSocketIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `/user/queue/connection/control` 아웃바운드 채널의 e2e 테스트.
 *
 * <p>이 채널은 클라이언트가 보내는 명령이 없는 <b>순수 서버 푸시</b>라 `/app/...` 전송으로 촉발할 수 없다.
 * 대신 실제 트리거인 관리자 API(`POST /admin/connection/draining`)를 실제 HTTP로 호출해 촉발한다.
 * 그래서 관리자 인증 · 컨트롤러 · 서비스 · notifier · Redis 릴레이 · `/user` 개인화 · JSON 직렬화까지
 * 전 구간이 실제로 관통한다.
 */
@TestPropertySource(properties = "admin.api-token=" + ConnectionControlWebSocketIntegrationTest.ADMIN_TOKEN)
class ConnectionControlWebSocketIntegrationTest extends AbstractWebSocketIntegrationTest {

    static final String ADMIN_TOKEN = "admin-ws-integration-token";

    private static final String CONTROL_QUEUE = "/user/queue/connection/control";

    @Autowired
    ConnectionTicketRepository connectionTicketRepository;

    @AfterEach
    void cleanUp() {
        // @Transactional 롤백을 못 쓰므로 커밋된 픽스처를 FK 안전 순서로 수동 정리한다.
        connectionTicketRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("draining 예고 e2e: 관리자 API 호출이 접속 중인 유저의 개인 control 큐까지 실제로 도달한다")
    void notifyDraining_end_to_end() throws Exception {
        // given: 유저 1명을 커밋하고 티켓으로 실제 STOMP 연결 후 control 큐 구독
        User me = saveUser();
        UUID ticket = issueWsTicket(me.getId());

        StompSession session = connect(ticket);
        BlockingQueue<WebSocketControlBody> controlQueue =
                subscribe(session, CONTROL_QUEUE, WebSocketControlBody.class);

        // when: 관리자가 배포 사유로 3초 후 재연결을 예고 (실제 HTTP → Redis 릴레이 → 소켓)
        requestDraining("""
                {"reason":"deploy","retryAfterMs":3000,"scope":"all"}
                """);

        // then: 개인 control 큐로 control 봉투 수신
        WebSocketControlBody draining = controlQueue.poll(5, TimeUnit.SECONDS);
        assertThat(draining).isNotNull();
        assertThat(draining.v()).isEqualTo(1);
        assertThat(draining.kind()).isEqualTo(WebSocketBodyKind.CONTROL);
        assertThat(draining.type()).isEqualTo(WebSocketBodyType.CONNECTION_DRAINING);

        // then: payload는 제네릭이라 Map으로 역직렬화된다. enum이 @JsonProperty 값("deploy")으로 나가는지 확인
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) draining.payload();
        assertThat(payload.get("reason")).isEqualTo("deploy");
        assertThat(((Number) payload.get("retryAfterMs")).longValue()).isEqualTo(3_000L);

        session.disconnect();
    }

    @Test
    @DisplayName("draining 예고는 접속 중인 유저 전원에게 나가며, 다른 유저의 프레임이 섞이지 않는다")
    void notifyDraining_fansOutToEachConnectedUserPrivately() throws Exception {
        // given: 유저 2명이 각자 티켓으로 접속해 자기 control 큐를 구독 (티켓은 단발성이라 각각 발급)
        User first = saveUser();
        User second = saveUser();

        StompSession firstSession = connect(issueWsTicket(first.getId()));
        StompSession secondSession = connect(issueWsTicket(second.getId()));
        BlockingQueue<WebSocketControlBody> firstQueue =
                subscribe(firstSession, CONTROL_QUEUE, WebSocketControlBody.class);
        BlockingQueue<WebSocketControlBody> secondQueue =
                subscribe(secondSession, CONTROL_QUEUE, WebSocketControlBody.class);

        // when: 관리자가 점검 사유로 대기 시간 없이 예고
        requestDraining("""
                {"reason":"maintenance","scope":"all"}
                """);

        // then: 두 세션 모두 각자 1건씩 수신하고, retryAfterMs=null 이 그대로 전달된다
        for (BlockingQueue<WebSocketControlBody> queue : List.of(firstQueue, secondQueue)) {
            WebSocketControlBody draining = queue.poll(5, TimeUnit.SECONDS);
            assertThat(draining).isNotNull();
            assertThat(draining.type()).isEqualTo(WebSocketBodyType.CONNECTION_DRAINING);

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) draining.payload();
            assertThat(payload.get("reason")).isEqualTo("maintenance");
            assertThat(payload.get("retryAfterMs")).isNull();

            // 개인 큐이므로 중복 수신이 없어야 한다
            assertThat(queue.poll(500, TimeUnit.MILLISECONDS)).isNull();
        }

        firstSession.disconnect();
        secondSession.disconnect();
    }

    /** 실제 서블릿 포트로 관리자 draining API를 호출한다. MockMvc가 아니라 진짜 HTTP다. */
    private void requestDraining(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/connection/draining"))
                .header("Content-Type", "application/json")
                .header("X-Admin-Token", ADMIN_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}

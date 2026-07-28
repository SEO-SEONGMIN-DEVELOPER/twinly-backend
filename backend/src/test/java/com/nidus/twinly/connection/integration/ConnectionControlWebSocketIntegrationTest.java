package com.nidus.twinly.connection.integration;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketControlBody;
import com.nidus.twinly.common.websocket.notifier.ConnectionControlNotifier;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import com.nidus.twinly.support.AbstractWebSocketIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `/user/queue/connection/control` 아웃바운드 채널의 e2e 테스트.
 *
 * <p>이 채널은 클라이언트가 보내는 명령이 없는 <b>순수 서버 푸시</b>라 `/app/...` 전송으로 촉발할 수 없다.
 * 게다가 운영 코드에 {@code notifyDraining} 호출자가 아직 없어서(= 트리거 미구현) 테스트가 notifier 빈을
 * 직접 호출해 촉발한다. 그래도 실제 소켓 · 실제 SimpUserRegistry 등록 · `/user` 개인화 · JSON 직렬화는
 * 전부 실제로 관통하므로 단위 테스트가 못 잡는 부분을 검증한다.
 */
class ConnectionControlWebSocketIntegrationTest extends AbstractWebSocketIntegrationTest {

    @Autowired
    ConnectionControlNotifier connectionControlNotifier;

    @Autowired
    ConnectionTicketRepository connectionTicketRepository;

    @AfterEach
    void cleanUp() {
        // @Transactional 롤백을 못 쓰므로 커밋된 픽스처를 FK 안전 순서로 수동 정리한다.
        connectionTicketRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("draining 예고 e2e: 접속 중인 유저의 개인 control 큐로 control 프레임이 실제로 전달된다")
    void notifyDraining_end_to_end() throws Exception {
        // given: 유저 1명을 커밋하고 티켓으로 실제 STOMP 연결 후 control 큐 구독
        User me = saveUser();
        UUID ticket = issueWsTicket(me.getId());

        StompSession session = connect(ticket);
        BlockingQueue<WebSocketControlBody> controlQueue =
                subscribe(session, "/user/queue/connection/control", WebSocketControlBody.class);

        // when: 서버가 배포 사유로 3초 후 재연결을 예고 (운영 호출자가 없어 notifier를 직접 호출)
        connectionControlNotifier.notifyDraining(ConnectionDrainingReason.DEPLOY, 3_000L);

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
                subscribe(firstSession, "/user/queue/connection/control", WebSocketControlBody.class);
        BlockingQueue<WebSocketControlBody> secondQueue =
                subscribe(secondSession, "/user/queue/connection/control", WebSocketControlBody.class);

        // when: 점검 사유로 대기 시간 없이 예고
        connectionControlNotifier.notifyDraining(ConnectionDrainingReason.MAINTENANCE, null);

        // then: 두 세션 모두 각자 1건씩 수신하고, retryAfterMs=null 이 그대로 전달된다
        for (BlockingQueue<WebSocketControlBody> queue : java.util.List.of(firstQueue, secondQueue)) {
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
}

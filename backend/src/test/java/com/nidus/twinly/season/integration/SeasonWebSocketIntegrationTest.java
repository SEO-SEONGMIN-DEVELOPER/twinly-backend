package com.nidus.twinly.season.integration;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.connection.repository.ConnectionTicketRepository;
import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.result.SeasonChangeResult;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.season.service.SeasonService;
import com.nidus.twinly.support.AbstractWebSocketIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `/user/queue/season` 아웃바운드 채널의 e2e 테스트.
 *
 * <p>SeasonNotifier 는 AFTER_COMMIT 리스너라 커밋이 일어나야 발화한다. 기존 시즌 통합 테스트는
 * AbstractIntegrationTest 를 상속해 매 테스트를 롤백하므로, 시즌 전환 API 를 호출해도 이 리스너는
 * 한 번도 실행되지 않았다. 그래서 이미 비트랜잭션인 WebSocket 기반 위에서 검증한다.
 *
 * <p>HTTP 계층은 SeasonAdminIntegrationTest 가 이미 덮으므로 여기서는 서비스를 직접 호출해
 * 커밋 → 리스너 발화 → 릴레이 → 소켓 구간만 관통시킨다.
 */
class SeasonWebSocketIntegrationTest extends AbstractWebSocketIntegrationTest {

    private static final String SEASON_QUEUE = "/user/queue/season";

    @Autowired
    SeasonService seasonService;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    ConnectionTicketRepository connectionTicketRepository;

    @AfterEach
    void cleanUp() {
        // @Transactional 롤백을 못 쓰므로 커밋된 픽스처를 FK 안전 순서로 수동 정리한다.
        connectionTicketRepository.deleteAll();
        seasonRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("시즌 전환 e2e: 커밋 이후 발화하는 알림이 접속 중인 유저의 season 큐까지 도달한다")
    void seasonChanged_end_to_end() throws Exception {
        // given: 유저 1명을 커밋하고 티켓으로 실제 STOMP 연결 후 season 큐 구독
        User me = saveUser();
        UUID ticket = issueWsTicket(me.getId());

        StompSession session = connect(ticket);
        BlockingQueue<WebSocketEventBody> seasonQueue =
                subscribe(session, SEASON_QUEUE, WebSocketEventBody.class);

        // when: 시즌 전환 (서비스의 트랜잭션이 커밋되어야 AFTER_COMMIT 리스너가 돈다)
        SeasonChangeResult changed = seasonService.changeSeason(new SeasonChangeCommand(
                Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(30))));

        // then: season 큐로 event 봉투 수신
        WebSocketEventBody body = seasonQueue.poll(5, TimeUnit.SECONDS);
        assertThat(body).isNotNull();
        assertThat(body.type()).isEqualTo(WebSocketBodyType.SEASON_CHANGED);

        // then: payload는 제네릭이라 Map으로 역직렬화된다. id는 이 프로젝트 규약대로 문자열로 실린다
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) body.payload();
        assertThat(payload.get("seasonId")).isEqualTo(String.valueOf(changed.seasonId()));

        session.disconnect();
    }
}

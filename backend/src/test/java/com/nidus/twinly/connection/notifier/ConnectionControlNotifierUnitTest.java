package com.nidus.twinly.connection.notifier;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketControlBody;
import com.nidus.twinly.common.websocket.relay.WebSocketRelayPublisher;
import com.nidus.twinly.common.websocket.sender.WebSocketLocalSender;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.domain.ConnectionDrainingScope;
import com.nidus.twinly.connection.dto.websocket.ConnectionDrainingPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ConnectionControlNotifierUnitTest {

    /** 서버가 전송할 때 쓰는 주소. 클라이언트 구독 주소(/user/queue/connection/control)와 달리 /user 가 없다. */
    private static final String CONTROL_DESTINATION = "/queue/connection/control";

    @Mock
    WebSocketRelayPublisher relayPublisher;

    @Mock
    WebSocketLocalSender localSender;

    @InjectMocks
    ConnectionControlNotifier notifier;

    @Test
    @DisplayName("scope=ALL 이면 릴레이로 전파해 모든 인스턴스의 접속자에게 전달한다")
    void notifyDraining_relaysToAllOnce() {
        // given: draining 은 특정 유저가 아니라 서버 전체의 사건이다.
        // 어느 유저가 어느 인스턴스에 붙어 있는지는 수신 측 Dispatcher가 판단한다.

        // when: 배포 사유로 3초 후 재연결을 예고
        notifier.notifyDraining(ConnectionDrainingReason.DEPLOY, 3_000L, ConnectionDrainingScope.ALL);

        // then: 유저별 전송이 아니라 전원 대상 전파 1회
        ArgumentCaptor<WebSocketControlBody> captor = ArgumentCaptor.forClass(WebSocketControlBody.class);
        then(relayPublisher).should().publishToAll(eq(CONTROL_DESTINATION), captor.capture());
        then(localSender).should(never()).sendToAll(any(), any());

        WebSocketControlBody sent = captor.getValue();
        assertThat(sent.v()).isEqualTo(1);
        assertThat(sent.kind()).isEqualTo(WebSocketBodyKind.CONTROL);
        assertThat(sent.type()).isEqualTo(WebSocketBodyType.CONNECTION_DRAINING);

        ConnectionDrainingPayload payload = (ConnectionDrainingPayload) sent.payload();
        assertThat(payload.reason()).isEqualTo(ConnectionDrainingReason.DEPLOY);
        assertThat(payload.retryAfterMs()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("scope=LOCAL 이면 릴레이를 타지 않고 이 인스턴스 접속자에게만 전달한다")
    void notifyDraining_localScopeBypassesRelay() {
        // given: 순차 배포에서 재기동할 인스턴스의 접속자에게만 예고해야 한다.
        // 릴레이를 타면 다른 인스턴스 접속자까지 끊기고, Redis 장애 시 예고 자체가 나가지 못한다.

        // when: 로컬 범위로 배포 예고
        notifier.notifyDraining(ConnectionDrainingReason.DEPLOY, 3_000L, ConnectionDrainingScope.LOCAL);

        // then: 릴레이는 호출되지 않고 로컬 전송만 일어난다
        ArgumentCaptor<WebSocketControlBody> captor = ArgumentCaptor.forClass(WebSocketControlBody.class);
        then(localSender).should().sendToAll(eq(CONTROL_DESTINATION), captor.capture());
        then(relayPublisher).should(never()).publishToAll(any(), any());

        WebSocketControlBody sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(WebSocketBodyType.CONNECTION_DRAINING);

        ConnectionDrainingPayload payload = (ConnectionDrainingPayload) sent.payload();
        assertThat(payload.reason()).isEqualTo(ConnectionDrainingReason.DEPLOY);
        assertThat(payload.retryAfterMs()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("retryAfterMs를 지정하지 않으면 null 그대로 실어 보낸다 (재연결 시점은 클라이언트가 정한다)")
    void notifyDraining_allowsNullRetryAfterMs() {
        // when: 대기 시간을 지정하지 않고 점검 사유로 예고
        notifier.notifyDraining(ConnectionDrainingReason.MAINTENANCE, null, ConnectionDrainingScope.ALL);

        // then: payload.retryAfterMs 는 null (스펙상 nullable)
        ArgumentCaptor<WebSocketControlBody> captor = ArgumentCaptor.forClass(WebSocketControlBody.class);
        then(relayPublisher).should().publishToAll(eq(CONTROL_DESTINATION), captor.capture());

        ConnectionDrainingPayload payload = (ConnectionDrainingPayload) captor.getValue().payload();
        assertThat(payload.reason()).isEqualTo(ConnectionDrainingReason.MAINTENANCE);
        assertThat(payload.retryAfterMs()).isNull();
    }
}

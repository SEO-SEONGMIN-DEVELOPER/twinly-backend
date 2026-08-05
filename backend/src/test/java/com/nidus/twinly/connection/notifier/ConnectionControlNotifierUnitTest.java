package com.nidus.twinly.connection.notifier;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketControlBody;
import com.nidus.twinly.common.websocket.relay.WebSocketRelayPublisher;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.dto.websocket.ConnectionDrainingPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ConnectionControlNotifierUnitTest {

    /** 서버가 전송할 때 쓰는 주소. 클라이언트 구독 주소(/user/queue/connection/control)와 달리 /user 가 없다. */
    private static final String CONTROL_DESTINATION = "/queue/connection/control";

    @Mock
    WebSocketRelayPublisher relayPublisher;

    @InjectMocks
    ConnectionControlNotifier notifier;

    @Test
    @DisplayName("draining 예고는 접속 중인 전원 대상으로 control 봉투를 한 번 전파한다")
    void notifyDraining_relaysToAllOnce() {
        // given: draining 은 특정 유저가 아니라 서버 전체의 사건이다.
        // 어느 유저가 어느 인스턴스에 붙어 있는지는 수신 측 Dispatcher가 판단한다.

        // when: 배포 사유로 3초 후 재연결을 예고
        notifier.notifyDraining(ConnectionDrainingReason.DEPLOY, 3_000L);

        // then: 유저별 전송이 아니라 전원 대상 전파 1회
        ArgumentCaptor<WebSocketControlBody> captor = ArgumentCaptor.forClass(WebSocketControlBody.class);
        then(relayPublisher).should().publishToAll(eq(CONTROL_DESTINATION), captor.capture());

        WebSocketControlBody sent = captor.getValue();
        assertThat(sent.v()).isEqualTo(1);
        assertThat(sent.kind()).isEqualTo(WebSocketBodyKind.CONTROL);
        assertThat(sent.type()).isEqualTo(WebSocketBodyType.CONNECTION_DRAINING);

        ConnectionDrainingPayload payload = (ConnectionDrainingPayload) sent.payload();
        assertThat(payload.reason()).isEqualTo(ConnectionDrainingReason.DEPLOY);
        assertThat(payload.retryAfterMs()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("retryAfterMs를 지정하지 않으면 null 그대로 실어 보낸다 (재연결 시점은 클라이언트가 정한다)")
    void notifyDraining_allowsNullRetryAfterMs() {
        // when: 대기 시간을 지정하지 않고 점검 사유로 예고
        notifier.notifyDraining(ConnectionDrainingReason.MAINTENANCE, null);

        // then: payload.retryAfterMs 는 null (스펙상 nullable)
        ArgumentCaptor<WebSocketControlBody> captor = ArgumentCaptor.forClass(WebSocketControlBody.class);
        then(relayPublisher).should().publishToAll(eq(CONTROL_DESTINATION), captor.capture());

        ConnectionDrainingPayload payload = (ConnectionDrainingPayload) captor.getValue().payload();
        assertThat(payload.reason()).isEqualTo(ConnectionDrainingReason.MAINTENANCE);
        assertThat(payload.retryAfterMs()).isNull();
    }
}

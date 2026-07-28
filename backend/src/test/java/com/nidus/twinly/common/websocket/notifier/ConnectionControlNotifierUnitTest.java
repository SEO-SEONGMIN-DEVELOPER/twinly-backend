package com.nidus.twinly.common.websocket.notifier;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketControlBody;
import com.nidus.twinly.connection.domain.ConnectionDrainingReason;
import com.nidus.twinly.connection.dto.websocket.ConnectionDrainingPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ConnectionControlNotifierUnitTest {

    /** 서버가 전송할 때 쓰는 주소. 클라이언트 구독 주소(/user/queue/connection/control)와 달리 /user 가 없다. */
    private static final String CONTROL_DESTINATION = "/queue/connection/control";

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    SimpUserRegistry simpUserRegistry;

    @InjectMocks
    ConnectionControlNotifier notifier;

    @Test
    @DisplayName("draining 예고는 접속 중인 모든 유저의 개인 큐로 control 봉투에 담아 팬아웃 전송한다")
    void notifyDraining_fansOutToAllConnectedUsers() {
        // given: 현재 유저 "1", "2" 가 접속 중
        // (mock 생성·스터빙을 given(...) 인자 안에서 하면 스터빙이 중첩되어 UnfinishedStubbingException 이 난다)
        Set<SimpUser> connected = Set.of(simpUser("1"), simpUser("2"));
        given(simpUserRegistry.getUsers()).willReturn(connected);

        // when: 배포 사유로 3초 후 재연결을 예고
        notifier.notifyDraining(ConnectionDrainingReason.DEPLOY, 3_000L);

        // then: 두 유저 각각에게 control 봉투가 전송됨 (봉투 인스턴스는 공유된다)
        for (String userId : List.of("1", "2")) {
            ArgumentCaptor<WebSocketControlBody> captor = ArgumentCaptor.forClass(WebSocketControlBody.class);
            then(messagingTemplate).should()
                    .convertAndSendToUser(eq(userId), eq(CONTROL_DESTINATION), captor.capture());

            WebSocketControlBody sent = captor.getValue();
            assertThat(sent.v()).isEqualTo(1);
            assertThat(sent.kind()).isEqualTo(WebSocketBodyKind.CONTROL);
            assertThat(sent.type()).isEqualTo(WebSocketBodyType.CONNECTION_DRAINING);

            ConnectionDrainingPayload payload = (ConnectionDrainingPayload) sent.payload();
            assertThat(payload.reason()).isEqualTo(ConnectionDrainingReason.DEPLOY);
            assertThat(payload.retryAfterMs()).isEqualTo(3_000L);
        }
    }

    @Test
    @DisplayName("retryAfterMs를 지정하지 않으면 null 그대로 실어 보낸다 (재연결 시점은 클라이언트가 정한다)")
    void notifyDraining_allowsNullRetryAfterMs() {
        // given: 유저 "1" 만 접속 중
        Set<SimpUser> connected = Set.of(simpUser("1"));
        given(simpUserRegistry.getUsers()).willReturn(connected);

        // when: 대기 시간을 지정하지 않고 점검 사유로 예고
        notifier.notifyDraining(ConnectionDrainingReason.MAINTENANCE, null);

        // then: payload.retryAfterMs 는 null (스펙상 nullable)
        ArgumentCaptor<WebSocketControlBody> captor = ArgumentCaptor.forClass(WebSocketControlBody.class);
        then(messagingTemplate).should()
                .convertAndSendToUser(eq("1"), eq(CONTROL_DESTINATION), captor.capture());

        ConnectionDrainingPayload payload = (ConnectionDrainingPayload) captor.getValue().payload();
        assertThat(payload.reason()).isEqualTo(ConnectionDrainingReason.MAINTENANCE);
        assertThat(payload.retryAfterMs()).isNull();
    }

    @Test
    @DisplayName("접속 중인 유저가 한 명도 없으면 아무것도 전송하지 않는다")
    void notifyDraining_noConnectedUsers_sendsNothing() {
        // given: 접속자 없음
        given(simpUserRegistry.getUsers()).willReturn(Set.of());

        // when
        notifier.notifyDraining(ConnectionDrainingReason.OVERLOAD, 1_000L);

        // then: 전송 호출 자체가 없어야 한다
        then(messagingTemplate).should(never())
                .convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    /** SimpUserRegistry가 돌려주는 접속 유저. 이 notifier는 이름(=userId 문자열)만 쓴다. */
    private SimpUser simpUser(String name) {
        SimpUser user = mock(SimpUser.class);
        willReturn(name).given(user).getName();
        return user;
    }
}

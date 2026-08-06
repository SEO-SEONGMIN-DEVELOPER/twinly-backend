package com.nidus.twinly.season.notifier;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.common.websocket.relay.WebSocketRelayPublisher;
import com.nidus.twinly.season.dto.websocket.SeasonChangedPayload;
import com.nidus.twinly.season.event.SeasonChangedEvent;
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
class SeasonNotifierUnitTest {

    @Mock
    WebSocketRelayPublisher relayPublisher;

    @InjectMocks
    SeasonNotifier seasonNotifier;

    @Test
    @DisplayName("시즌 전환 이벤트를 받으면 접속 중인 전원 대상으로 season.changed를 한 번 전파한다")
    void onSeasonChanged_relaysToAllOnce() {
        // given: 시즌 전환은 특정 유저가 아니라 전역 사건이다.
        // 어느 유저가 어느 인스턴스에 붙어 있는지는 수신 측 Dispatcher가 판단한다.

        // when
        seasonNotifier.onSeasonChanged(new SeasonChangedEvent(7L));

        // then: 유저별 전송이 아니라 전원 대상 전파 1회
        ArgumentCaptor<WebSocketEventBody> captor = ArgumentCaptor.forClass(WebSocketEventBody.class);
        then(relayPublisher).should().publishToAll(eq("/queue/season"), captor.capture());

        assertThat(captor.getValue().type()).isEqualTo(WebSocketBodyType.SEASON_CHANGED);
        assertThat(captor.getValue().payload()).isEqualTo(new SeasonChangedPayload(7L));
    }
}

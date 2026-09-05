package com.nidus.twinly.common.websocket.relay;

import com.nidus.twinly.common.logging.TraceContext;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.common.websocket.sender.WebSocketLocalSender;
import com.nidus.twinly.season.dto.websocket.SeasonChangedPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Dispatcher의 책임은 릴레이 메시지를 읽어 <b>어느 방향으로 보낼지 고르는 것</b>이다.
 * 실제로 접속자에게 전달하는 팬아웃은 {@link WebSocketLocalSender} 가 맡으므로
 * 그 동작은 {@link com.nidus.twinly.common.websocket.sender.WebSocketLocalSenderUnitTest} 가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketRelayDispatcherUnitTest {

    @Mock
    RedisSerializer<WebSocketRelayMessage> webSocketRelaySerializer;

    @Mock
    WebSocketLocalSender webSocketLocalSender;

    @InjectMocks
    WebSocketRelayDispatcher dispatcher;

    @Test
    @DisplayName("userId가 있으면 그 유저에게만 보내고 전원 전달은 하지 않는다")
    void onMessage_toUser() {
        // given
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(7L));
        givenRelayMessage(WebSocketRelayMessage.toUser("42", "/queue/season", body, "a3f9c210"));

        // when
        dispatcher.onMessage(message(), null);

        // then
        then(webSocketLocalSender).should().sendToUser(eq("42"), eq("/queue/season"), eq(body));
        then(webSocketLocalSender).should(never()).sendToAll(any(), any());
    }

    @Test
    @DisplayName("userId가 없으면 이 인스턴스에 접속한 유저 전원 대상으로 보낸다")
    void onMessage_toAllLocalUsers() {
        // given: 수신자를 특정하지 않은 릴레이 메시지
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(7L));
        givenRelayMessage(WebSocketRelayMessage.toAll("/queue/season", body, "a3f9c210"));

        // when
        dispatcher.onMessage(message(), null);

        // then
        then(webSocketLocalSender).should().sendToAll(eq("/queue/season"), eq(body));
        then(webSocketLocalSender).should(never()).sendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("역직렬화에 실패해도 예외를 밖으로 던지지 않는다")
    void onMessage_swallowsDeserializationFailure() {
        // given: 구독이 끊기면 안 되므로 메시지 하나의 실패는 삼켜야 한다
        given(webSocketRelaySerializer.deserialize(any())).willThrow(new SerializationException("깨진 JSON"));

        // when
        dispatcher.onMessage(message(), null);

        // then
        then(webSocketLocalSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("보낸 서버의 traceId 를 이어받아 처리한다")
    void onMessage_carries_trace_id() {
        // given: 다른 서버가 traceId 를 실어 보낸 메시지
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(7L));
        givenRelayMessage(WebSocketRelayMessage.toUser("42", "/queue/season", body, "a3f9c210"));

        AtomicReference<String> seen = new AtomicReference<>();
        willAnswer(invocation -> seen.getAndSet(MDC.get(TraceContext.TRACE_ID)))
                .given(webSocketLocalSender).sendToUser(any(), any(), any());

        // when
        dispatcher.onMessage(message(), null);

        // then: 서버를 넘어와도 같은 traceId 로 묶인다
        assertThat(seen.get()).isEqualTo("a3f9c210");
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("traceId 가 없는 메시지도 자기 traceId 를 갖고 처리된다")
    void onMessage_falls_back_when_trace_id_missing() {
        // given: traceId 를 싣지 않던 이전 버전 서버가 보낸 메시지
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload(7L));
        givenRelayMessage(WebSocketRelayMessage.toUser("42", "/queue/season", body, null));

        AtomicReference<String> seen = new AtomicReference<>();
        willAnswer(invocation -> seen.getAndSet(MDC.get(TraceContext.TRACE_ID)))
                .given(webSocketLocalSender).sendToUser(any(), any(), any());

        // when
        dispatcher.onMessage(message(), null);

        // then: 원인 요청과는 못 잇지만, 이 서버 안에서는 묶을 수 있어야 한다
        assertThat(seen.get()).isNotNull();
    }

    private void givenRelayMessage(WebSocketRelayMessage relayMessage) {
        given(webSocketRelaySerializer.deserialize(any())).willReturn(relayMessage);
    }

    private Message message() {
        Message message = mock(Message.class);
        given(message.getBody()).willReturn(new byte[0]);
        return message;
    }
}

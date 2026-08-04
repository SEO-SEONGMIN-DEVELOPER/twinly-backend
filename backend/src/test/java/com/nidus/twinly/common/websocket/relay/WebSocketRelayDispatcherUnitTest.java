package com.nidus.twinly.common.websocket.relay;

import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import com.nidus.twinly.season.dto.websocket.SeasonChangedPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WebSocketRelayDispatcherUnitTest {

    @Mock
    RedisSerializer<WebSocketRelayMessage> webSocketRelaySerializer;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    SimpUserRegistry simpUserRegistry;

    @InjectMocks
    WebSocketRelayDispatcher dispatcher;

    @Test
    @DisplayName("userId가 있으면 그 유저에게만 전달한다")
    void onMessage_toUser() {
        // given
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload());
        givenRelayMessage(WebSocketRelayMessage.toUser("42", "/queue/season", body));

        // when
        dispatcher.onMessage(message(), null);

        // then
        then(messagingTemplate).should().convertAndSendToUser(eq("42"), eq("/queue/season"), eq(body));
        then(simpUserRegistry).should(never()).getUsers();
    }

    @Test
    @DisplayName("userId가 없으면 이 인스턴스에 접속한 유저 전원에게 전달한다")
    void onMessage_toAllLocalUsers() {
        // given: 이 인스턴스에는 1번과 2번이 붙어 있다
        WebSocketEventBody<SeasonChangedPayload> body = WebSocketEventBody.of(
                WebSocketBodyType.SEASON_CHANGED, new SeasonChangedPayload());
        givenRelayMessage(WebSocketRelayMessage.toAll("/queue/season", body));

        // mock 스터빙을 given(...) 인자 안에서 하면 중첩되어 UnfinishedStubbingException 이 난다
        Set<SimpUser> connected = Set.of(simpUser("1"), simpUser("2"));
        given(simpUserRegistry.getUsers()).willReturn(connected);

        // when
        dispatcher.onMessage(message(), null);

        // then
        then(messagingTemplate).should().convertAndSendToUser(eq("1"), eq("/queue/season"), eq(body));
        then(messagingTemplate).should().convertAndSendToUser(eq("2"), eq("/queue/season"), eq(body));
    }

    @Test
    @DisplayName("역직렬화에 실패해도 예외를 밖으로 던지지 않는다")
    void onMessage_swallowsDeserializationFailure() {
        // given: 구독이 끊기면 안 되므로 메시지 하나의 실패는 삼켜야 한다
        given(webSocketRelaySerializer.deserialize(any())).willThrow(new SerializationException("깨진 JSON"));

        // when
        dispatcher.onMessage(message(), null);

        // then
        then(messagingTemplate).shouldHaveNoInteractions();
    }

    private void givenRelayMessage(WebSocketRelayMessage relayMessage) {
        given(webSocketRelaySerializer.deserialize(any())).willReturn(relayMessage);
    }

    private Message message() {
        Message message = mock(Message.class);
        given(message.getBody()).willReturn(new byte[0]);
        return message;
    }

    private SimpUser simpUser(String name) {
        SimpUser user = mock(SimpUser.class);
        given(user.getName()).willReturn(name);
        return user;
    }
}

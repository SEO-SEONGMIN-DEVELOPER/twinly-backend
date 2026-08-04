package com.nidus.twinly.chat.notifier;

import com.nidus.twinly.chat.domain.ChatMessageType;
import com.nidus.twinly.chat.domain.ChatSenderType;
import com.nidus.twinly.chat.dto.websocket.ChatChangedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageCreatedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadAdvancedPayload;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.event.ChatChangedEvent;
import com.nidus.twinly.chat.event.ChatMessageCreatedEvent;
import com.nidus.twinly.chat.event.ChatReadAdvancedEvent;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.nidus.twinly.common.websocket.relay.WebSocketRelayPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatNotifierUnitTest {

    @Mock
    WebSocketRelayPublisher relayPublisher;

    @InjectMocks
    ChatNotifier chatNotifier;

    @Test
    @DisplayName("메시지 생성 시 참여자별로 개인 큐에 전송하며 발신자는 ME+clientMsgId, 상대는 THEM+clientMsgId=null로 구분한다")
    void onChatMessageCreated_sendsPerParticipant_withMeThemBranch() {
        // given: 발신자 1 → 수신자 2 로 보낸 채팅. 참여자는 [1(발신자), 2(수신자)]
        Chat chat = Chat.create("cmid-1", 10L, 1L, 2L, ChatMessageType.TEXT, "안녕");
        ReflectionTestUtils.setField(chat, "id", 100L);
        var event = new ChatMessageCreatedEvent(chat, List.of(1L, 2L));

        // when: 트랜잭션 커밋 후 리스너 메서드를 직접 호출
        chatNotifier.onChatMessageCreated(event);

        // then: 발신자 "1"에게 ME + 원래 clientMsgId 로 전송
        ArgumentCaptor<WebSocketEventBody> senderCaptor = ArgumentCaptor.forClass(WebSocketEventBody.class);
        then(relayPublisher).should()
                .publishToUser(eq("1"), eq("/queue/chat/rooms/10"), senderCaptor.capture());
        assertThat(senderCaptor.getValue().type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_CREATED);
        ChatMessageCreatedPayload toSender = (ChatMessageCreatedPayload) senderCaptor.getValue().payload();
        assertThat(toSender.message().senderType()).isEqualTo(ChatSenderType.ME);
        assertThat(toSender.message().clientMsgId()).isEqualTo("cmid-1");
        assertThat(toSender.message().messageId()).isEqualTo(100L);

        // then: 수신자 "2"에게 THEM + clientMsgId 는 노출하지 않음(null)
        ArgumentCaptor<WebSocketEventBody> receiverCaptor = ArgumentCaptor.forClass(WebSocketEventBody.class);
        then(relayPublisher).should()
                .publishToUser(eq("2"), eq("/queue/chat/rooms/10"), receiverCaptor.capture());
        ChatMessageCreatedPayload toReceiver = (ChatMessageCreatedPayload) receiverCaptor.getValue().payload();
        assertThat(toReceiver.message().senderType()).isEqualTo(ChatSenderType.THEM);
        assertThat(toReceiver.message().clientMsgId()).isNull();
    }

    @Test
    @DisplayName("읽음 위치 이동 시 대상 참여자의 방 큐에만 CHAT_READ_ADVANCED 이벤트를 전송한다")
    void onChatReadAdvanced_sendsRoomEventToTargetsOnly() {
        // given: 방 10에서 1번이 77까지 읽었고, 통지 대상은 상대인 2번뿐
        var event = new ChatReadAdvancedEvent(10L, 77L, List.of(2L));

        // when: 트랜잭션 커밋 후 리스너 메서드를 직접 호출
        chatNotifier.onChatReadAdvanced(event);

        // then: 상대(2)의 방 큐로 roomId·lastReadMessageId가 담긴 이벤트가 나감
        ArgumentCaptor<WebSocketEventBody> captor = ArgumentCaptor.forClass(WebSocketEventBody.class);
        then(relayPublisher).should()
                .publishToUser(eq("2"), eq("/queue/chat/rooms/10"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(WebSocketBodyType.CHAT_READ_ADVANCED);

        ChatReadAdvancedPayload payload = (ChatReadAdvancedPayload) captor.getValue().payload();
        assertThat(payload.roomId()).isEqualTo(10L);
        assertThat(payload.lastReadMessageId()).isEqualTo(77L);

        // then: 읽은 본인(1)에게는 아무것도 나가지 않음
        then(relayPublisher).should(never())
                .publishToUser(eq("1"), anyString(), any());
    }

    @Test
    @DisplayName("채팅방 변경 시 참여자별로 index 큐에 CHAT_CHANGED 이벤트를 전송한다")
    void onChatChanged_sendsIndexToEachParticipant() {
        // given: 방 10 변경, 참여자 [1, 2]
        var event = new ChatChangedEvent(10L, List.of(1L, 2L));

        // when
        chatNotifier.onChatChanged(event);

        // then: 두 참여자 각각의 /queue/chat/index 로 CHAT_CHANGED 전송 (roomId=10)
        for (String userId : List.of("1", "2")) {
            ArgumentCaptor<WebSocketEventBody> captor = ArgumentCaptor.forClass(WebSocketEventBody.class);
            then(relayPublisher).should()
                    .publishToUser(eq(userId), eq("/queue/chat/index"), captor.capture());
            assertThat(captor.getValue().type()).isEqualTo(WebSocketBodyType.CHAT_CHANGED);
            assertThat(((ChatChangedPayload) captor.getValue().payload()).roomId()).isEqualTo(10L);
        }
    }
}

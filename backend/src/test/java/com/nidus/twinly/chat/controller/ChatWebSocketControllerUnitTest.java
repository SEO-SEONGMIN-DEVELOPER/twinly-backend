package com.nidus.twinly.chat.controller;

import com.nidus.twinly.chat.dto.command.ChatReadMessagesCommand;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.result.ChatReadMessagesResult;
import com.nidus.twinly.chat.dto.result.ChatSendMessageResult;
import com.nidus.twinly.chat.dto.websocket.ChatMessageCommittedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageSendPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadAdvancePayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadCommittedPayload;
import com.nidus.twinly.chat.service.ChatService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketRequestBody;
import com.nidus.twinly.common.websocket.handshake.WebSocketUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerUnitTest {

    @Mock
    ChatService chatService;

    // 실제 전송(destination·봉투·에러코드 매핑)은 ChatCommandResponderUnitTest 의 몫이다.
    // 여기서는 "컨트롤러가 responder에 올바른 인자로 위임했는가"만 검증한다.
    @Mock
    ChatCommandResponder commandResponder;

    @InjectMocks
    ChatWebSocketController controller;

    @Test
    @DisplayName("메시지 전송 성공 시 서비스에 위임하고 committed 응답을 요청자에게 위임한다")
    void sendMessage_success() {
        // given: 유저 1의 정상 COMMAND 봉투, 서비스는 성공 결과를 반환
        Principal principal = new WebSocketUserPrincipal(1L);
        var body = new WebSocketRequestBody<>(1, WebSocketBodyKind.COMMAND,
                WebSocketBodyType.CHAT_MESSAGE_SEND, "command-1",
                new ChatMessageSendPayload(10L, "cmid-1", "안녕"));
        given(chatService.sendMessage(eq(1L), eq(10L), any(ChatSendMessageCommand.class)))
                .willReturn(new ChatSendMessageResult(100L, "안녕", Instant.parse("2026-01-01T00:00:00Z"), "cmid-1"));

        // when: @MessageMapping 메서드를 직접 호출
        controller.sendMessage(principal, "session-A", body);

        // then: 인증 유저 id·roomId로 서비스에 위임 (payload 내용까지 확인)
        ArgumentCaptor<ChatSendMessageCommand> commandCaptor = ArgumentCaptor.forClass(ChatSendMessageCommand.class);
        then(chatService).should().sendMessage(eq(1L), eq(10L), commandCaptor.capture());
        assertThat(commandCaptor.getValue().text()).isEqualTo("안녕");
        assertThat(commandCaptor.getValue().clientMsgId()).isEqualTo("cmid-1");

        // then: committed 응답을 서비스 결과로 채워 responder에 위임
        ArgumentCaptor<ChatMessageCommittedPayload> payloadCaptor = ArgumentCaptor.forClass(ChatMessageCommittedPayload.class);
        then(commandResponder).should().messageCommitted(eq(1L), eq("session-A"), eq("command-1"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().roomId()).isEqualTo(10L);
        assertThat(payloadCaptor.getValue().messageId()).isEqualTo(100L);
        assertThat(payloadCaptor.getValue().clientMsgId()).isEqualTo("cmid-1");
        assertThat(payloadCaptor.getValue().text()).isEqualTo("안녕");
    }

    @Test
    @DisplayName("전송 중 서비스가 BusinessException을 던지면 밖으로 던지지 않고 rejected 응답을 위임한다")
    void sendMessage_businessException_delegatesRejected() {
        // given: 서비스가 도메인 예외(방 없음)를 던지도록 설정
        Principal principal = new WebSocketUserPrincipal(1L);
        var body = new WebSocketRequestBody<>(1, WebSocketBodyKind.COMMAND,
                WebSocketBodyType.CHAT_MESSAGE_SEND, "command-1",
                new ChatMessageSendPayload(10L, "cmid-1", "안녕"));
        BusinessException exception = new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        given(chatService.sendMessage(anyLong(), anyLong(), any())).willThrow(exception);

        // when: 예외가 발생해도 컨트롤러는 정상 반환(프레임으로 응답)
        controller.sendMessage(principal, "session-A", body);

        // then: 예외를 그대로 넘겨 rejected 응답을 위임 (에러코드 변환은 responder 책임)
        then(commandResponder).should().messageRejected(1L, "session-A", "command-1", 10L, "cmid-1", exception);
    }

    @Test
    @DisplayName("읽음 처리 성공 시 서비스에 위임하고 read committed 응답을 요청자에게 위임한다")
    void readMessages_success() {
        // given: 유저 1의 정상 읽음 COMMAND 봉투, 서비스는 성공 결과를 반환
        Principal principal = new WebSocketUserPrincipal(1L);
        var body = new WebSocketRequestBody<>(1, WebSocketBodyKind.COMMAND,
                WebSocketBodyType.CHAT_READ_ADVANCE, "command-2",
                new ChatReadAdvancePayload(10L, 55L));
        given(chatService.readMessages(eq(1L), eq(10L), any(ChatReadMessagesCommand.class)))
                .willReturn(new ChatReadMessagesResult(10L, 55L));

        // when
        controller.readMessages(principal, "session-A", body);

        // then: 서비스 결과(roomId·lastMessageId)로 read committed 응답을 위임
        ArgumentCaptor<ChatReadCommittedPayload> payloadCaptor = ArgumentCaptor.forClass(ChatReadCommittedPayload.class);
        then(commandResponder).should().readCommitted(eq(1L), eq("session-A"), eq("command-2"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().roomId()).isEqualTo(10L);
        assertThat(payloadCaptor.getValue().lastMsgId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("읽음 처리 중 서비스가 BusinessException을 던지면 rejected 응답을 위임한다")
    void readMessages_businessException_delegatesRejected() {
        // given: 서비스가 도메인 예외를 던지도록 설정
        Principal principal = new WebSocketUserPrincipal(1L);
        var body = new WebSocketRequestBody<>(1, WebSocketBodyKind.COMMAND,
                WebSocketBodyType.CHAT_READ_ADVANCE, "command-2",
                new ChatReadAdvancePayload(10L, 55L));
        BusinessException exception = new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        given(chatService.readMessages(anyLong(), anyLong(), any())).willThrow(exception);

        // when
        controller.readMessages(principal, "session-A", body);

        // then: 요청 payload 값과 예외를 그대로 넘겨 rejected 응답을 위임
        then(commandResponder).should().readRejected(1L, "session-A", "command-2", 10L, 55L, exception);
    }

    @Test
    @DisplayName("봉투의 type이 매핑과 다르면 IllegalArgumentException을 던지고 서비스·응답을 하지 않는다")
    void sendMessage_invalidEnvelope_throwsAndDoesNotDelegate() {
        // given: /chat/messages 매핑인데 type을 CHAT_READ_ADVANCE로 잘못 넣은 봉투
        Principal principal = new WebSocketUserPrincipal(1L);
        var body = new WebSocketRequestBody<>(1, WebSocketBodyKind.COMMAND,
                WebSocketBodyType.CHAT_READ_ADVANCE, "command-1",
                new ChatMessageSendPayload(10L, "cmid-1", "안녕"));

        // when & then: 봉투 검증 실패로 예외 발생 + 서비스·응답 모두 호출되지 않음
        assertThatThrownBy(() -> controller.sendMessage(principal, "session-A", body))
                .isInstanceOf(IllegalArgumentException.class);

        then(chatService).shouldHaveNoInteractions();
        then(commandResponder).shouldHaveNoInteractions();
    }
}

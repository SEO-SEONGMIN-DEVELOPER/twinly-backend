package com.nidus.twinly.chat.controller;

import com.nidus.twinly.common.websocket.domain.WebSocketErrorCode;
import com.nidus.twinly.chat.dto.websocket.ChatMessageCommittedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageRejectedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadCommittedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadRejectedPayload;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketCommandResultBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatCommandResponderUnitTest {

    /** 서버가 전송할 때 쓰는 주소. 클라이언트 구독 주소(/user/queue/chat/commands)와 달리 /user 가 없다. */
    private static final String COMMANDS_DESTINATION = "/queue/chat/commands";

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    ChatCommandResponder responder;

    @Test
    @DisplayName("메시지 커밋 응답을 요청자의 개인 큐로 command-result 봉투에 담아 전송한다")
    void messageCommitted_sendsCommandResult() {
        // given: 방 10에 저장된 메시지 100의 커밋 페이로드
        var payload = new ChatMessageCommittedPayload(10L, 100L, "cmid-1", "안녕", Instant.parse("2026-01-01T00:00:00Z"));

        // when: 유저 1에게 커밋 응답 전송
        responder.messageCommitted(1L, "session-A", "command-1", payload);

        // then: 유저 "1"의 /queue/chat/commands 로 command-result 봉투 전송
        WebSocketCommandResultBody sent = captureSentTo("1");
        assertThat(sent.v()).isEqualTo(1);
        assertThat(sent.kind()).isEqualTo(WebSocketBodyKind.COMMAND_RESULT);
        assertThat(sent.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_COMMITTED);
        assertThat(sent.commandId()).isEqualTo("command-1");
        assertThat(sent.payload()).isSameAs(payload);
    }

    @Test
    @DisplayName("읽음 커밋 응답을 요청자의 개인 큐로 command-result 봉투에 담아 전송한다")
    void readCommitted_sendsCommandResult() {
        // given: 방 10의 읽음 커서 55 커밋 페이로드
        var payload = new ChatReadCommittedPayload(10L, 55L);

        // when
        responder.readCommitted(1L, "session-A", "command-2", payload);

        // then
        WebSocketCommandResultBody sent = captureSentTo("1");
        assertThat(sent.type()).isEqualTo(WebSocketBodyType.CHAT_READ_COMMITTED);
        assertThat(sent.commandId()).isEqualTo("command-2");
    }

    @Test
    @DisplayName("메시지 거절 응답은 요청 정보를 담고 도메인 예외를 CommandError로 변환해 전송한다")
    void messageRejected_convertsExceptionToCommandError() {
        // given: 방을 찾지 못한 도메인 예외(404)
        BusinessException exception = new BusinessException(ErrorCode.ROOM_NOT_FOUND);

        // when: 유저 1에게 거절 응답 전송
        responder.messageRejected(1L, "session-A", "command-1", 10L, "cmid-1", exception);

        // then: rejected 봉투에 요청 정보(roomId·clientMsgId)와 변환된 에러가 담김
        WebSocketCommandResultBody sent = captureSentTo("1");
        assertThat(sent.type()).isEqualTo(WebSocketBodyType.CHAT_MESSAGE_REJECTED);
        assertThat(sent.commandId()).isEqualTo("command-1");

        ChatMessageRejectedPayload payload = (ChatMessageRejectedPayload) sent.payload();
        assertThat(payload.roomId()).isEqualTo(10L);
        assertThat(payload.clientMsgId()).isEqualTo("cmid-1");
        assertThat(payload.error().code()).isEqualTo(WebSocketErrorCode.ROOM_NOT_FOUND);
        assertThat(payload.error().message()).isEqualTo(ErrorCode.ROOM_NOT_FOUND.getDefaultMessage());
        assertThat(payload.error().retryable()).isFalse();
    }

    @Test
    @DisplayName("읽음 거절 응답은 요청 정보를 담고 도메인 예외를 CommandError로 변환해 전송한다")
    void readRejected_convertsExceptionToCommandError() {
        // given: 매칭 참여자가 아닌 도메인 예외(403)
        BusinessException exception = new BusinessException(ErrorCode.NOT_MATCH_PARTICIPANT);

        // when
        responder.readRejected(1L, "session-A", "command-2", 10L, 55L, exception);

        // then: rejected 봉투에 요청 정보(roomId·lastMsgId)와 변환된 에러가 담김
        WebSocketCommandResultBody sent = captureSentTo("1");
        assertThat(sent.type()).isEqualTo(WebSocketBodyType.CHAT_READ_REJECTED);

        ChatReadRejectedPayload payload = (ChatReadRejectedPayload) sent.payload();
        assertThat(payload.roomId()).isEqualTo(10L);
        assertThat(payload.lastMsgId()).isEqualTo(55L);
        assertThat(payload.error().code()).isEqualTo(WebSocketErrorCode.NOT_A_PARTICIPANT);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "ROOM_NOT_FOUND,               ROOM_NOT_FOUND",
            "MATCH_NOT_FOUND,              MATCH_NOT_FOUND",           // 404이지만 방 없음과 구분된다
            "CHAT_PARTICIPATION_NOT_FOUND, PARTICIPATION_NOT_FOUND",   // 〃
            "NOT_MATCH_PARTICIPANT,        NOT_A_PARTICIPANT",
            "NOT_ACTIVE_ROOM_PARTICIPANT,  NOT_ACTIVE_PARTICIPANT",    // 403이지만 미참여와 구분된다
            "ROOM_CLOSED,                  ROOM_CLOSED",               // 409이지만 clientMsgId 충돌과 구분된다
            "CLIENT_MSG_ID_CONFLICT,       CLIENT_MSG_ID_CONFLICT",
            "MESSAGE_LENGTH_EXCEEDED,      TEXT_SIZE_LIMIT_EXCEEDED",
            "MESSAGE_NOT_IN_ROOM,          INVALID_MESSAGE_CURSOR",
            "INTERNAL_ERROR,               INTERNAL"                   // 매핑되지 않은 코드
    })
    @DisplayName("메시지 거절은 status가 아니라 ErrorCode 자체로 WebSocketErrorCode를 정한다")
    void messageRejected_mapsErrorCodeToWebSocketErrorCode(ErrorCode errorCode, WebSocketErrorCode expected) {
        // when: 각 도메인 예외로 거절 응답 전송
        responder.messageRejected(1L, "session-A", "command-1", 10L, "cmid-1", new BusinessException(errorCode));

        // then: 같은 status를 공유하는 코드들도 서로 다른 WebSocketErrorCode로 갈린다
        ChatMessageRejectedPayload payload = (ChatMessageRejectedPayload) captureSentTo("1").payload();
        assertThat(payload.error().code()).isEqualTo(expected);
    }

    @Test
    @DisplayName("읽음 거절도 같은 규칙을 쓰므로 MESSAGE_NOT_IN_ROOM은 INVALID_MESSAGE_CURSOR가 된다")
    void readRejected_mapsMessageNotInRoomToInvalidCursor() {
        // given: 읽음 커서가 방에 없는 상황
        BusinessException exception = new BusinessException(ErrorCode.MESSAGE_NOT_IN_ROOM);

        // when
        responder.readRejected(1L, "session-A", "command-2", 10L, 55L, exception);

        // then
        ChatReadRejectedPayload payload = (ChatReadRejectedPayload) captureSentTo("1").payload();
        assertThat(payload.error().code()).isEqualTo(WebSocketErrorCode.INVALID_MESSAGE_CURSOR);
    }

    @Test
    @DisplayName("command-result는 명령을 보낸 세션 하나만 타겟팅하도록 simpSessionId 헤더를 실어 전송한다")
    void send_targetsOnlyRequestingSession() {
        // given: 같은 유저가 여러 기기로 접속한 상황 — 응답은 명령을 보낸 세션에만 가야 한다
        var payload = new ChatReadCommittedPayload(10L, 55L);

        // when: session-A 에서 온 명령에 대한 응답 전송
        responder.readCommitted(1L, "session-A", "command-2", payload);

        // then: 헤더에 세션이 실려야 resolver가 그 세션 큐 하나만 고른다 (없으면 전 기기 브로드캐스트)
        ArgumentCaptor<MessageHeaders> headersCaptor = ArgumentCaptor.forClass(MessageHeaders.class);
        then(messagingTemplate).should().convertAndSendToUser(
                eq("1"), eq(COMMANDS_DESTINATION), any(WebSocketCommandResultBody.class), headersCaptor.capture());

        MessageHeaders headers = headersCaptor.getValue();
        assertThat(SimpMessageHeaderAccessor.getSessionId(headers)).isEqualTo("session-A");

        // 전송 중 content-type 등이 덧붙을 때 세션 정보가 살아남으려면 헤더가 mutable 이어야 한다.
        // getAccessor 는 mutable 헤더일 때만 accessor 를 돌려주므로 leaveMutable(true) 검증이 된다.
        assertThat(MessageHeaderAccessor.getAccessor(headers, SimpMessageHeaderAccessor.class)).isNotNull();
    }

    @Test
    @DisplayName("sessionId 없이 전송하려 하면 전 기기 브로드캐스트로 폴백하지 않고 즉시 실패한다")
    void send_withoutSessionId_fails() {
        // given & when & then: 조용히 모든 기기로 새어나가는 것보다 터지는 편이 안전하다
        var payload = new ChatReadCommittedPayload(10L, 55L);

        assertThatThrownBy(() -> responder.readCommitted(1L, null, "command-2", payload))
                .isInstanceOf(IllegalArgumentException.class);

        then(messagingTemplate).shouldHaveNoInteractions();
    }

    /** 해당 유저의 개인 큐로 전송된 봉투를 캡처한다. */
    private WebSocketCommandResultBody captureSentTo(String userId) {
        ArgumentCaptor<WebSocketCommandResultBody> captor = ArgumentCaptor.forClass(WebSocketCommandResultBody.class);
        then(messagingTemplate).should().convertAndSendToUser(
                eq(userId), eq(COMMANDS_DESTINATION), captor.capture(), ArgumentMatchers.<String, Object>anyMap());
        return captor.getValue();
    }
}

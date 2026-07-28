package com.nidus.twinly.chat.controller;

import com.nidus.twinly.chat.domain.CommandErrorCode;
import com.nidus.twinly.chat.dto.websocket.ChatMessageCommittedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageRejectedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadCommittedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadRejectedPayload;
import com.nidus.twinly.chat.dto.websocket.CommandError;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketCommandResultBody;
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@RequiredArgsConstructor
public class ChatCommandResponder {

    private static final String COMMANDS_DESTINATION = "/queue/chat/commands";
    private static final String OUTBOUND_CHANNEL = "/user/queue/chat/commands";

    private final SimpMessagingTemplate messagingTemplate;

    public void messageCommitted(Long userId, String sessionId, String commandId, ChatMessageCommittedPayload payload) {
        publishMessageCommitted(userId, sessionId,
                WebSocketCommandResultBody.of(WebSocketBodyType.CHAT_MESSAGE_COMMITTED, commandId, payload));
    }

    public void messageRejected(Long userId, String sessionId, String commandId,
                                Long roomId, String clientMsgId, BusinessException e) {
        ChatMessageRejectedPayload payload = new ChatMessageRejectedPayload(
                roomId, clientMsgId, toCommandError(e, CommandErrorCode.TEXT_SIZE_LIMIT_EXCEEDED));

        publishMessageRejected(userId, sessionId,
                WebSocketCommandResultBody.of(WebSocketBodyType.CHAT_MESSAGE_REJECTED, commandId, payload));
    }

    public void readCommitted(Long userId, String sessionId, String commandId, ChatReadCommittedPayload payload) {
        publishReadCommitted(userId, sessionId,
                WebSocketCommandResultBody.of(WebSocketBodyType.CHAT_READ_COMMITTED, commandId, payload));
    }

    public void readRejected(Long userId, String sessionId, String commandId,
                             Long roomId, Long lastMsgId, BusinessException e) {
        ChatReadRejectedPayload payload = new ChatReadRejectedPayload(
                roomId, lastMsgId, toCommandError(e, CommandErrorCode.INVALID_MESSAGE_CURSOR));

        publishReadRejected(userId, sessionId,
                WebSocketCommandResultBody.of(WebSocketBodyType.CHAT_READ_REJECTED, commandId, payload));
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = OUTBOUND_CHANNEL,
            description = "채팅 메시지 전송 성공(커밋)"
    ))
    @StompAsyncOperationBinding
    public void publishMessageCommitted(Long userId, String sessionId,
                                        @Payload WebSocketCommandResultBody<ChatMessageCommittedPayload> body) {
        send(userId, sessionId, body);
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = OUTBOUND_CHANNEL,
            description = "채팅 메시지 전송 실패(거절)"
    ))
    @StompAsyncOperationBinding
    public void publishMessageRejected(Long userId, String sessionId,
                                       @Payload WebSocketCommandResultBody<ChatMessageRejectedPayload> body) {
        send(userId, sessionId, body);
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = OUTBOUND_CHANNEL,
            description = "읽음 처리 성공(커밋)"
    ))
    @StompAsyncOperationBinding
    public void publishReadCommitted(Long userId, String sessionId,
                                     @Payload WebSocketCommandResultBody<ChatReadCommittedPayload> body) {
        send(userId, sessionId, body);
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = OUTBOUND_CHANNEL,
            description = "읽음 처리 실패(거절)"
    ))
    @StompAsyncOperationBinding
    public void publishReadRejected(Long userId, String sessionId,
                                    @Payload WebSocketCommandResultBody<ChatReadRejectedPayload> body) {
        send(userId, sessionId, body);
    }

    private void send(Long userId, String sessionId, WebSocketCommandResultBody<?> body) {
        Assert.hasText(sessionId, "command-result 전송에는 sessionId가 필요합니다.");

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);

        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId), COMMANDS_DESTINATION, body, accessor.getMessageHeaders());
    }

    private CommandError toCommandError(BusinessException e, CommandErrorCode unprocessableCode) {
        CommandErrorCode code = switch (e.getErrorCode().getStatus().value()) {
            case 409 -> CommandErrorCode.CLIENT_MSG_ID_CONFLICT;
            case 422 -> unprocessableCode;
            case 403 -> CommandErrorCode.NOT_A_PARTICIPANT;
            case 404 -> CommandErrorCode.ROOM_NOT_FOUND;
            default -> CommandErrorCode.INTERNAL;
        };

        return new CommandError(code, e.getMessage(), false);
    }
}

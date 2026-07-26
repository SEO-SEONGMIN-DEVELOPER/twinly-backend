package com.nidus.twinly.chat.controller;

import com.nidus.twinly.chat.dto.command.ChatReadMessagesCommand;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.result.ChatReadMessagesResult;
import com.nidus.twinly.chat.dto.result.ChatSendMessageResult;
import com.nidus.twinly.chat.service.ChatService;
import com.nidus.twinly.chat.domain.CommandErrorCode;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.chat.dto.websocket.ChatMessageCommittedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageRejectedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageSendPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadAdvancePayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadCommittedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadRejectedPayload;
import com.nidus.twinly.chat.dto.websocket.CommandError;
import com.nidus.twinly.common.websocket.dto.WebSocketRequestBody;
import com.nidus.twinly.common.websocket.dto.WebSocketResponseBody;
import com.nidus.twinly.common.websocket.handshake.WebSocketUserPrincipal;
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private static final String COMMANDS_DESTINATION = "/queue/chat/commands";
    private static final String OUTBOUND_CHANNEL = "/user/queue/chat/commands";

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/messages")
    public void sendMessage(Principal principal,
                            @Valid @Payload WebSocketRequestBody<ChatMessageSendPayload> body) {
        assertCommandEnvelope(body, WebSocketBodyType.CHAT_MESSAGE_SEND);

        Long userId = ((WebSocketUserPrincipal) principal).userId();
        ChatMessageSendPayload payload = body.payload();

        try {
            ChatSendMessageResult result = chatService.sendMessage(userId, payload.roomId(),
                    new ChatSendMessageCommand(payload.text(), payload.clientMsgId()));

            publishMessageCommitted(userId, body.commandId(),
                    new ChatMessageCommittedPayload(payload.roomId(), result.messageId(), result.clientMsgId(), result.text(), result.sentAt()));
        } catch (BusinessException e) {
            publishMessageRejected(userId, body.commandId(),
                    new ChatMessageRejectedPayload(payload.roomId(), payload.clientMsgId(), toCommandError(e, CommandErrorCode.TEXT_SIZE_LIMIT_EXCEEDED)));
        }
    }

    @MessageMapping("/chat/read")
    public void readMessages(Principal principal,
                             @Valid @Payload WebSocketRequestBody<ChatReadAdvancePayload> body) {
        assertCommandEnvelope(body, WebSocketBodyType.CHAT_READ_ADVANCE);

        Long userId = ((WebSocketUserPrincipal) principal).userId();
        ChatReadAdvancePayload payload = body.payload();

        try {
            ChatReadMessagesResult result = chatService.readMessages(userId, payload.roomId(),
                    new ChatReadMessagesCommand(payload.lastMsgId()));

            publishReadCommitted(userId, body.commandId(),
                    new ChatReadCommittedPayload(result.roomId(), result.lastMessageId()));
        } catch (BusinessException e) {
            publishReadRejected(userId, body.commandId(),
                    new ChatReadRejectedPayload(payload.roomId(), payload.lastMsgId(), toCommandError(e, CommandErrorCode.INVALID_MESSAGE_CURSOR)));
        }
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = OUTBOUND_CHANNEL,
            description = "채팅 메시지 전송 성공(커밋)",
            payloadType = ChatMessageCommittedPayload.class
    ))
    @StompAsyncOperationBinding
    public void publishMessageCommitted(Long userId, String commandId, ChatMessageCommittedPayload payload) {
        sendToCommands(userId, WebSocketResponseBody.commandResult(commandId, WebSocketBodyType.CHAT_MESSAGE_COMMITTED, payload));
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = OUTBOUND_CHANNEL,
            description = "채팅 메시지 전송 실패(거절)",
            payloadType = ChatMessageRejectedPayload.class
    ))
    @StompAsyncOperationBinding
    public void publishMessageRejected(Long userId, String commandId, ChatMessageRejectedPayload payload) {
        sendToCommands(userId, WebSocketResponseBody.commandResult(commandId, WebSocketBodyType.CHAT_MESSAGE_REJECTED, payload));
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = OUTBOUND_CHANNEL,
            description = "읽음 처리 성공(커밋)",
            payloadType = ChatReadCommittedPayload.class
    ))
    @StompAsyncOperationBinding
    public void publishReadCommitted(Long userId, String commandId, ChatReadCommittedPayload payload) {
        sendToCommands(userId, WebSocketResponseBody.commandResult(commandId, WebSocketBodyType.CHAT_READ_COMMITTED, payload));
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = OUTBOUND_CHANNEL,
            description = "읽음 처리 실패(거절)",
            payloadType = ChatReadRejectedPayload.class
    ))
    @StompAsyncOperationBinding
    public void publishReadRejected(Long userId, String commandId, ChatReadRejectedPayload payload) {
        sendToCommands(userId, WebSocketResponseBody.commandResult(commandId, WebSocketBodyType.CHAT_READ_REJECTED, payload));
    }

    private void assertCommandEnvelope(WebSocketRequestBody<?> body, WebSocketBodyType expectedType) {
        if (body.v() == null || body.v() != 1
                || !WebSocketBodyKind.COMMAND.equals(body.kind())
                || body.type() != expectedType) {
            throw new IllegalArgumentException("허용되지 않은 COMMAND Body입니다.");
        }
    }

    private void sendToCommands(Long userId, WebSocketResponseBody body) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), COMMANDS_DESTINATION, body);
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

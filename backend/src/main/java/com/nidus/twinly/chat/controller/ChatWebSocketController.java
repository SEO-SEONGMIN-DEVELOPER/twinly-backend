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

            sendToCommands(userId, WebSocketResponseBody.commandResult(
                    body.commandId(),
                    WebSocketBodyType.CHAT_MESSAGE_COMMITTED,
                    new ChatMessageCommittedPayload(payload.roomId(), result.messageId(), result.clientMsgId(), result.text(), result.sentAt())));
        } catch (BusinessException e) {
            sendToCommands(userId, WebSocketResponseBody.commandResult(
                    body.commandId(),
                    WebSocketBodyType.CHAT_MESSAGE_REJECTED,
                    new ChatMessageRejectedPayload(payload.roomId(), payload.clientMsgId(), toCommandError(e, CommandErrorCode.TEXT_SIZE_LIMIT_EXCEEDED))));
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

            sendToCommands(userId, WebSocketResponseBody.commandResult(
                    body.commandId(),
                    WebSocketBodyType.CHAT_READ_COMMITTED,
                    new ChatReadCommittedPayload(result.roomId(), result.lastMessageId())));
        } catch (BusinessException e) {
            sendToCommands(userId, WebSocketResponseBody.commandResult(
                    body.commandId(),
                    WebSocketBodyType.CHAT_READ_REJECTED,
                    new ChatReadRejectedPayload(payload.roomId(), payload.lastMsgId(), toCommandError(e, CommandErrorCode.INVALID_MESSAGE_CURSOR))));
        }
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

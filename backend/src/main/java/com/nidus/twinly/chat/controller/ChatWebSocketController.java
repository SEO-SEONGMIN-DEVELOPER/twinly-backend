package com.nidus.twinly.chat.controller;

import com.nidus.twinly.chat.dto.command.ChatReadMessagesCommand;
import com.nidus.twinly.chat.dto.command.ChatSendMessageCommand;
import com.nidus.twinly.chat.dto.result.ChatReadMessagesResult;
import com.nidus.twinly.chat.dto.result.ChatSendMessageResult;
import com.nidus.twinly.chat.service.ChatService;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyKind;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.chat.dto.websocket.ChatMessageCommittedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageSendPayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadAdvancePayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadCommittedPayload;
import com.nidus.twinly.common.websocket.dto.WebSocketRequestBody;
import com.nidus.twinly.common.websocket.handshake.WebSocketUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final ChatCommandResponder commandResponder;

    @MessageMapping("/chat/messages")
    public void sendMessage(Principal principal,
                            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId,
                            @Valid @Payload WebSocketRequestBody<ChatMessageSendPayload> body) {
        assertCommandEnvelope(body, WebSocketBodyType.CHAT_MESSAGE_SEND);

        Long userId = ((WebSocketUserPrincipal) principal).userId();
        ChatMessageSendPayload payload = body.payload();

        try {
            ChatSendMessageResult result = chatService.sendMessage(userId, payload.roomId(),
                    new ChatSendMessageCommand(payload.text(), payload.clientMsgId()));

            commandResponder.messageCommitted(userId, sessionId, body.commandId(),
                    new ChatMessageCommittedPayload(payload.roomId(), result.messageId(), result.clientMsgId(), result.text(), result.sentAt()));
        } catch (BusinessException e) {
            commandResponder.messageRejected(userId, sessionId, body.commandId(), payload.roomId(), payload.clientMsgId(), e);
        }
    }

    @MessageMapping("/chat/read")
    public void readMessages(Principal principal,
                             @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId,
                             @Valid @Payload WebSocketRequestBody<ChatReadAdvancePayload> body) {
        assertCommandEnvelope(body, WebSocketBodyType.CHAT_READ_ADVANCE);

        Long userId = ((WebSocketUserPrincipal) principal).userId();
        ChatReadAdvancePayload payload = body.payload();

        try {
            ChatReadMessagesResult result = chatService.readMessages(userId, payload.roomId(),
                    new ChatReadMessagesCommand(payload.lastMsgId()));

            commandResponder.readCommitted(userId, sessionId, body.commandId(),
                    new ChatReadCommittedPayload(result.roomId(), result.lastMessageId()));
        } catch (BusinessException e) {
            commandResponder.readRejected(userId, sessionId, body.commandId(), payload.roomId(), payload.lastMsgId(), e);
        }
    }

    private void assertCommandEnvelope(WebSocketRequestBody<?> body, WebSocketBodyType expectedType) {
        if (body.v() == null || body.v() != 1
                || !WebSocketBodyKind.COMMAND.equals(body.kind())
                || body.type() != expectedType) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}

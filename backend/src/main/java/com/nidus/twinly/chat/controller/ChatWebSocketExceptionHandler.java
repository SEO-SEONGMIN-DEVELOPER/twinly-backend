package com.nidus.twinly.chat.controller;

import com.nidus.twinly.common.logging.Actor;
import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.common.websocket.domain.WebSocketErrorCode;
import com.nidus.twinly.common.websocket.handshake.WebSocketUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(assignableTypes = ChatWebSocketController.class)
@RequiredArgsConstructor
public class ChatWebSocketExceptionHandler {

    private static final String SEND_DESTINATION = "/app/chat/messages";
    private static final String READ_DESTINATION = "/app/chat/read";

    private final ObjectProvider<ChatCommandResponder> commandResponder;
    private final ObjectMapper objectMapper;

    @MessageExceptionHandler({
            BusinessException.class,
            MethodArgumentNotValidException.class,
            MessageConversionException.class
    })
    public void handleInvalidCommand(Exception e, Message<?> message, Principal principal) {
        reject(message, principal, ErrorCode.INVALID_REQUEST);
    }

    @MessageExceptionHandler(Exception.class)
    public void handleUnexpected(Exception e, Message<?> message, Principal principal) {
        ErrorLog.error(log, WebSocketErrorCode.INTERNAL.name(), actorOf(principal), e)
                .log("웹소켓 메시지 처리에 실패했습니다");
        reject(message, principal, ErrorCode.INTERNAL_ERROR);
    }

    private String actorOf(Principal principal) {
        return principal instanceof WebSocketUserPrincipal user ? Actor.user(user.userId()) : null;
    }

    private void reject(Message<?> message, Principal principal, ErrorCode errorCode) {
        ChatCommandResponder responder = commandResponder.getIfAvailable();
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
        String sessionId = headers.getSessionId();
        String destination = headers.getDestination();
        JsonNode body = readBody(message);

        if (responder == null || sessionId == null || body == null
                || !(principal instanceof WebSocketUserPrincipal user)) {
            return;
        }

        String commandId = text(body.path("commandId"));
        if (commandId == null) {
            return;
        }

        BusinessException rejection = new BusinessException(errorCode);
        JsonNode payload = body.path("payload");
        Long roomId = number(payload.path("roomId"));

        if (READ_DESTINATION.equals(destination)) {
            responder.readRejected(user.userId(), sessionId, commandId, roomId, number(payload.path("lastMsgId")), rejection);
            return;
        }

        if (SEND_DESTINATION.equals(destination)) {
            responder.messageRejected(user.userId(), sessionId, commandId, roomId, text(payload.path("clientMsgId")), rejection);
        }
    }

    private JsonNode readBody(Message<?> message) {
        if (!(message.getPayload() instanceof byte[] bytes)) {
            return null;
        }

        try {
            return objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            return null;
        }
    }

    private String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }

    private Long number(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }
        if (!node.isString()) {
            return null;
        }
        try {
            return Long.valueOf(node.asString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

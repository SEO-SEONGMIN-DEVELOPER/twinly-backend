package com.nidus.twinly.common.websocket.interceptor;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WebSocketFrameValidationInterceptor implements ChannelInterceptor {

    private static final int MAX_BODY_BYTES = 32 * 1024;
    private static final int MAX_HEADER_COUNT = 32;
    private static final int MAX_HEADER_KEY_BYTES = 64;
    private static final int MAX_HEADER_VALUE_BYTES = 1024;

    private static final Set<String> ALLOWED_SEND_DESTINATIONS = Set.of(
            "/app/chat/messages",
            "/app/chat/read"
    );
    private static final String ALLOWED_SUBSCRIBE_PREFIX = "/user/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (command == null) {
            return message;
        }

        if (command == StompCommand.SUBSCRIBE) {
            validateSubscribeDestination(message, accessor.getDestination());
            return message;
        }

        if (command != StompCommand.SEND) {
            return message;
        }

        validateSendDestination(message, accessor.getDestination());

        if (bodyByteLength(message) > MAX_BODY_BYTES) {
            throw new MessagingException(message, "본문 크기 상한을 초과했습니다.");
        }

        validateHeaders(message, accessor);

        return message;
    }

    private void validateSubscribeDestination(Message<?> message, String destination) {
        if (destination == null || !destination.startsWith(ALLOWED_SUBSCRIBE_PREFIX)) {
            throw new MessagingException(message, "허용되지 않은 구독 목적지입니다.");
        }
    }

    private void validateSendDestination(Message<?> message, String destination) {
        if (destination == null || !ALLOWED_SEND_DESTINATIONS.contains(destination)) {
            throw new MessagingException(message, "허용되지 않은 전송 목적지입니다.");
        }
    }

    private void validateHeaders(Message<?> message, StompHeaderAccessor accessor) {
        Map<String, List<String>> nativeHeaders = accessor.toNativeHeaderMap();

        int headerLineCount = nativeHeaders.values().stream().mapToInt(List::size).sum();
        if (headerLineCount > MAX_HEADER_COUNT) {
            throw new MessagingException(message, "헤더 개수 상한을 초과했습니다.");
        }

        nativeHeaders.forEach((key, values) -> {
            if (utf8Length(key) > MAX_HEADER_KEY_BYTES) {
                throw new MessagingException(message, "헤더 키 상한을 초과했습니다.");
            }
            for (String value : values) {
                if (value != null && utf8Length(value) > MAX_HEADER_VALUE_BYTES) {
                    throw new MessagingException(message, "헤더 값 상한을 초과했습니다.");
                }
            }
        });
    }

    private int bodyByteLength(Message<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof byte[] bytes) {
            return bytes.length;
        }

        return 0;
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}

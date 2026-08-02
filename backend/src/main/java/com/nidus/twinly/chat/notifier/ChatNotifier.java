package com.nidus.twinly.chat.notifier;

import com.nidus.twinly.chat.domain.ChatSenderType;
import com.nidus.twinly.chat.dto.websocket.ChatChangedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageCreatedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessagePayload;
import com.nidus.twinly.chat.dto.websocket.ChatReadAdvancedPayload;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.event.ChatChangedEvent;
import com.nidus.twinly.chat.event.ChatMessageCreatedEvent;
import com.nidus.twinly.chat.event.ChatReadAdvancedEvent;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketEventBody;
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ChatNotifier {

    private static final String ROOM_DESTINATION_PREFIX = "/queue/chat/rooms/";
    private static final String INDEX_DESTINATION = "/queue/chat/index";

    private static final String ROOM_OUTBOUND_CHANNEL = "/user/queue/chat/rooms/{roomId}";
    private static final String INDEX_OUTBOUND_CHANNEL = "/user/queue/chat/index";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageCreated(ChatMessageCreatedEvent event) {
        Chat chat = event.chat();
        String encodedRoomId = encodePathSegment(String.valueOf(chat.getRoomId()));

        for (Long participantUserId : event.participantUserIds()) {
            ChatSenderType senderType = chat.getSenderUserId().equals(participantUserId)
                    ? ChatSenderType.ME
                    : ChatSenderType.THEM;

            ChatMessagePayload message = new ChatMessagePayload(
                    chat.getId(),
                    senderType,
                    chat.getMessage(),
                    chat.getSentAt(),
                    senderType == ChatSenderType.ME ? chat.getClientMsgId() : null);

            publishMessageCreated(participantUserId, encodedRoomId,
                    WebSocketEventBody.of(WebSocketBodyType.CHAT_MESSAGE_CREATED,
                            new ChatMessageCreatedPayload(chat.getRoomId(), message)));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatReadAdvanced(ChatReadAdvancedEvent event) {
        String encodedRoomId = encodePathSegment(String.valueOf(event.roomId()));

        for (Long targetUserId : event.targetUserIds()) {
            publishReadAdvanced(targetUserId, encodedRoomId,
                    WebSocketEventBody.of(WebSocketBodyType.CHAT_READ_ADVANCED,
                            new ChatReadAdvancedPayload(event.roomId(), event.lastReadMessageId())));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatChanged(ChatChangedEvent event) {
        for (Long participantUserId : event.participantUserIds()) {
            publishChatChanged(participantUserId,
                    WebSocketEventBody.of(WebSocketBodyType.CHAT_CHANGED, new ChatChangedPayload(event.roomId())));
        }
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = ROOM_OUTBOUND_CHANNEL,
            description = "채팅방에 생성된 새 메시지 (senderType은 수신자 기준 me/them)"
    ))
    @StompAsyncOperationBinding
    public void publishMessageCreated(Long userId, String encodedRoomId,
                                      @Payload WebSocketEventBody<ChatMessageCreatedPayload> body) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), ROOM_DESTINATION_PREFIX + encodedRoomId, body);
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = ROOM_OUTBOUND_CHANNEL,
            description = "상대가 읽은 마지막 메시지 id (읽음 표시 갱신)"
    ))
    @StompAsyncOperationBinding
    public void publishReadAdvanced(Long userId, String encodedRoomId,
                                    @Payload WebSocketEventBody<ChatReadAdvancedPayload> body) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), ROOM_DESTINATION_PREFIX + encodedRoomId, body);
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = INDEX_OUTBOUND_CHANNEL,
            description = "채팅방 목록 갱신 필요 (목록 재조회 트리거)"
    ))
    @StompAsyncOperationBinding
    public void publishChatChanged(Long userId, @Payload WebSocketEventBody<ChatChangedPayload> body) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), INDEX_DESTINATION, body);
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}

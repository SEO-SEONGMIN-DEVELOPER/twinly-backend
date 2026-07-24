package com.nidus.twinly.chat.notifier;

import com.nidus.twinly.chat.domain.ChatSenderType;
import com.nidus.twinly.chat.dto.websocket.ChatChangedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessageCreatedPayload;
import com.nidus.twinly.chat.dto.websocket.ChatMessagePayload;
import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.event.ChatChangedEvent;
import com.nidus.twinly.chat.event.ChatMessageCreatedEvent;
import com.nidus.twinly.common.websocket.domain.WebSocketBodyType;
import com.nidus.twinly.common.websocket.dto.WebSocketResponseBody;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ChatNotifier {

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

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(participantUserId),
                    "/queue/chat/rooms/" + encodedRoomId,
                    WebSocketResponseBody.event(WebSocketBodyType.CHAT_MESSAGE_CREATED,
                            new ChatMessageCreatedPayload(chat.getRoomId(), message)));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatChanged(ChatChangedEvent event) {
        for (Long participantUserId : event.participantUserIds()) {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(participantUserId),
                    "/queue/chat/index",
                    WebSocketResponseBody.event(WebSocketBodyType.CHAT_CHANGED, new ChatChangedPayload(event.roomId())));
        }
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}

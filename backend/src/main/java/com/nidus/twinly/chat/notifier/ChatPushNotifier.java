package com.nidus.twinly.chat.notifier;

import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.chat.event.ChatMessageCreatedEvent;
import com.nidus.twinly.common.fcm.ChatMessagePushContent;
import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatPushNotifier {

    private final PushRecipientResolver pushRecipientResolver;
    private final PushMessageBuilder pushMessageBuilder;
    private final FcmSender fcmSender;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageCreated(ChatMessageCreatedEvent event) {
        Chat chat = event.chat();

        List<Long> receiverUserIds = event.participantUserIds().stream()
                .filter(participantUserId -> !participantUserId.equals(chat.getSenderUserId()))
                .toList();

        List<Device> devices = pushRecipientResolver.resolve(receiverUserIds, NotificationType.CHAT);
        if (devices.isEmpty()) {
            return;
        }

        userRepository.findById(chat.getSenderUserId())
                .map(sender -> content(chat, sender))
                .ifPresent(content -> fcmSender.send(pushMessageBuilder.chatMessage(devices, content)));
    }

    private ChatMessagePushContent content(Chat chat, User sender) {
        return new ChatMessagePushContent(
                chat.getId(),
                chat.getRoomId(),
                chat.getSenderUserId(),
                sender.displayGivenName(),
                chat.getMessage(),
                thumbnailKey(chat.getSenderUserId()),
                chat.getSentAt());
    }

    private String thumbnailKey(Long senderUserId) {
        return photoRepository.findByUserIdAndType(senderUserId, PhotoType.PROFILE)
                .map(Photo::getThumbnailKey)
                .orElse(null);
    }
}

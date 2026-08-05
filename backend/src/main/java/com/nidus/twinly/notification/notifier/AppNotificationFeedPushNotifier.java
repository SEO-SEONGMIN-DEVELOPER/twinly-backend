package com.nidus.twinly.notification.notifier;

import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.FeedPushContent;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.event.AppNotificationFeedCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AppNotificationFeedPushNotifier {

    private final PushRecipientResolver pushRecipientResolver;
    private final PushMessageBuilder pushMessageBuilder;
    private final FcmSender fcmSender;


    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppNotificationFeedCreated(AppNotificationFeedCreatedEvent event) {
        for (AppNotificationFeed feed : event.feeds()) {
            List<Device> devices = pushRecipientResolver.resolve(List.of(feed.getUserId()), NotificationType.EVENT);
            if (devices.isEmpty()) {
                continue;
            }

            fcmSender.send(pushMessageBuilder.feed(devices, content(feed)));
        }
    }

    private FeedPushContent content(AppNotificationFeed feed) {
        return new FeedPushContent(
                feed.getId(),
                feed.getType(),
                feed.getTitle(),
                feed.getBody(),
                feed.getTargetKind(),
                targetId(feed),
                feed.getCreatedAt());
    }

    private Long targetId(AppNotificationFeed feed) {
        return feed.getTargetKind() == AppNotificationFeedTargetType.CHAT
                ? feed.getTargetChatRoomId()
                : feed.getTargetUserId();
    }
}

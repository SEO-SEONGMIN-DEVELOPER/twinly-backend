package com.nidus.twinly.notification.notifier;

import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.FeedPushContent;
import com.nidus.twinly.common.fcm.PushMessage;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AppNotificationFeedPushNotifier {

    private static final int PUSH_BATCH_SIZE = 500;

    private final PushRecipientResolver pushRecipientResolver;
    private final PushMessageBuilder pushMessageBuilder;
    private final FcmSender fcmSender;

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppNotificationFeedCreated(AppNotificationFeedCreatedEvent event) {
        List<Long> userIds = event.feeds().stream()
                .map(AppNotificationFeed::getUserId)
                .distinct()
                .toList();

        Map<Long, List<Device>> devicesByUserId = pushRecipientResolver
                .resolve(userIds, NotificationType.EVENT).stream()
                .collect(Collectors.groupingBy(Device::getUserId));

        List<PushMessage> pushMessages = new ArrayList<>();
        for (AppNotificationFeed feed : event.feeds()) {
            List<Device> devices = devicesByUserId.get(feed.getUserId());
            if (devices == null) {
                continue;
            }

            pushMessages.addAll(pushMessageBuilder.feed(devices, content(feed)));
        }

        for (int i = 0; i < pushMessages.size(); i += PUSH_BATCH_SIZE) {
            fcmSender.send(pushMessages.subList(i, Math.min(i + PUSH_BATCH_SIZE, pushMessages.size())));
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
        if (feed.getTargetKind() == AppNotificationFeedTargetType.CHAT) {
            return feed.getTargetChatRoomId();
        }
        if (feed.getTargetKind() == AppNotificationFeedTargetType.SEASON_RECAP) {
            return feed.getTargetSeasonId();
        }
        return feed.getTargetUserId();
    }
}

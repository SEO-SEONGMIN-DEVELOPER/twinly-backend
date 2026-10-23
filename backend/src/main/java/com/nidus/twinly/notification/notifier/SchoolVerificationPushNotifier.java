package com.nidus.twinly.notification.notifier;

import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.fcm.SchoolVerificationPushContent;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.event.SchoolVerificationFeedCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SchoolVerificationPushNotifier {

    private final PushRecipientResolver pushRecipientResolver;
    private final PushMessageBuilder pushMessageBuilder;
    private final FcmSender fcmSender;

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSchoolVerificationFeedCreated(SchoolVerificationFeedCreatedEvent event) {
        AppNotificationFeed feed = event.feed();
        List<Device> devices = pushRecipientResolver.resolve(List.of(feed.getUserId()), NotificationType.EVENT);

        if (devices.isEmpty()) {
            return;
        }

        fcmSender.send(pushMessageBuilder.schoolVerification(devices, new SchoolVerificationPushContent(
                feed.getId(),
                event.status(),
                feed.getTitle(),
                feed.getBody(),
                feed.getCreatedAt())));
    }
}

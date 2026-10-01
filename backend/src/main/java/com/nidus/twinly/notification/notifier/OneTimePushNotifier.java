package com.nidus.twinly.notification.notifier;

import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.OneTimePushContent;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.event.OneTimePushEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OneTimePushNotifier {

    private final PushRecipientResolver pushRecipientResolver;
    private final PushMessageBuilder pushMessageBuilder;
    private final FcmSender fcmSender;

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOneTimePush(OneTimePushEvent event) {
        List<Device> devices = pushRecipientResolver.resolve(List.of(event.userId()), NotificationType.EVENT);
        if (devices.isEmpty()) {
            return;
        }

        fcmSender.send(pushMessageBuilder.oneTimePushOnly(
                devices, new OneTimePushContent(event.title(), event.body(), event.createdAt())));
    }
}

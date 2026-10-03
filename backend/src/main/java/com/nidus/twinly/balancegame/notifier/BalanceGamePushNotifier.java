package com.nidus.twinly.balancegame.notifier;

import com.nidus.twinly.balancegame.event.BalanceGameSummaryEvent;
import com.nidus.twinly.common.fcm.BalanceGameSummaryPushContent;
import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BalanceGamePushNotifier {

    private static final String TITLE = "밸런스 게임 결과가 나왔어요";
    private static final String BODY_FORMAT = "이번 질문에서 %d명과 같은 답을 골랐어요! 친밀도가 올랐어요";

    private final PushRecipientResolver pushRecipientResolver;
    private final PushMessageBuilder pushMessageBuilder;
    private final FcmSender fcmSender;

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBalanceGameSummary(BalanceGameSummaryEvent event) {
        Map<Long, List<Device>> devicesByUserId = pushRecipientResolver
                .resolve(List.copyOf(event.matchedCountByUserId().keySet()), NotificationType.EVENT).stream()
                .collect(Collectors.groupingBy(Device::getUserId));

        List<PushMessage> messages = devicesByUserId.entrySet().stream()
                .flatMap(entry -> {
                    Long matchedCount = event.matchedCountByUserId().get(entry.getKey());
                    return pushMessageBuilder.balanceGameSummary(entry.getValue(), new BalanceGameSummaryPushContent(
                            event.roundId(), matchedCount, TITLE, BODY_FORMAT.formatted(matchedCount), event.createdAt())).stream();
                })
                .toList();

        if (messages.isEmpty()) {
            return;
        }

        fcmSender.send(messages);
    }
}

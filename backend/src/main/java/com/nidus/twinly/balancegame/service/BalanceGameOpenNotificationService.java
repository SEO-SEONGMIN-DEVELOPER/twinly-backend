package com.nidus.twinly.balancegame.service;

import com.nidus.twinly.common.fcm.BalanceGameOpenedPushContent;
import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceGameOpenNotificationService {

    private static final String TITLE = "새 밸런스 게임이 열렸어요";
    private static final String BODY = "트윈들과 같은 답을 골라 친밀도를 올려 보세요.";
    private static final int PUSH_BATCH_SIZE = 500;

    private final SeasonRepository seasonRepository;
    private final RelationshipRepository relationshipRepository;
    private final PushRecipientResolver pushRecipientResolver;
    private final PushMessageBuilder pushMessageBuilder;
    private final FcmSender fcmSender;

    public void notifyOpenedRound(LocalDateTime kstNow) {
        if (seasonRepository.findFirstByIsActiveTrueOrderByIdDesc().isEmpty()) {
            InfoLog.log(log, "진행 중인 시즌이 없어 밸런스 게임 출제 알림을 건너뜁니다.");
            return;
        }

        List<Long> userIds = relationshipRepository.findUserIdsWithVisiblePartner(kstNow);
        List<PushMessage> pushMessages = pushMessageBuilder.balanceGameOpened(
                pushRecipientResolver.resolve(userIds, NotificationType.EVENT),
                new BalanceGameOpenedPushContent(TITLE, BODY, Instant.now()));

        for (int i = 0; i < pushMessages.size(); i += PUSH_BATCH_SIZE) {
            fcmSender.send(pushMessages.subList(i, Math.min(i + PUSH_BATCH_SIZE, pushMessages.size())));
        }

        InfoLog.log(log, "밸런스 게임 출제 알림을 발송했습니다.",
                field("userCount", userIds.size()), field("pushCount", pushMessages.size()));
    }
}

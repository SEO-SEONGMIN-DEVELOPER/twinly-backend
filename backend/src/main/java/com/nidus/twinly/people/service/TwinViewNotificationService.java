package com.nidus.twinly.people.service;

import com.nidus.twinly.common.fcm.FcmSender;
import com.nidus.twinly.common.fcm.FeedPushContent;
import com.nidus.twinly.common.fcm.PushMessage;
import com.nidus.twinly.common.fcm.PushMessageBuilder;
import com.nidus.twinly.common.fcm.PushRecipientResolver;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.device.entity.Device;
import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.notification.entity.AppNotificationFeed;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import com.nidus.twinly.people.repository.TwinViewRepository;
import com.nidus.twinly.people.repository.TwinViewRepository.ViewerCountProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwinViewNotificationService {

    private static final int INTERVAL_DAYS = 5;
    private static final LocalTime SEND_AT = LocalTime.of(20, 0);
    private static final int PUSH_BATCH_SIZE = 500;

    private final TwinViewRepository twinViewRepository;
    private final AppNotificationFeedRepository appNotificationFeedRepository;
    private final PushRecipientResolver pushRecipientResolver;
    private final PushMessageBuilder pushMessageBuilder;
    private final FcmSender fcmSender;

    public void notifyViewerCounts(LocalDate today) {
        if (today.toEpochDay() % INTERVAL_DAYS != 0) {
            return;
        }

        Instant to = today.atTime(SEND_AT).atZone(KstTimes.ZONE).toInstant();
        Instant from = to.minus(INTERVAL_DAYS, ChronoUnit.DAYS);

        Map<Long, Long> viewerCountByUserId = twinViewRepository
                .countDistinctViewersByViewedAtRange(from, to).stream()
                .collect(Collectors.toMap(ViewerCountProjection::getTargetUserId,
                        ViewerCountProjection::getViewerCount));

        if (viewerCountByUserId.isEmpty()) {
            return;
        }

        List<AppNotificationFeed> feeds = appNotificationFeedRepository.saveAll(
                viewerCountByUserId.entrySet().stream()
                        .map(entry -> feed(entry.getKey(), entry.getValue()))
                        .toList());

        sendPushes(feeds, viewerCountByUserId.keySet().stream().toList());

        log.info("트윈 열람 수 알림을 발송했습니다. feedCount={}", feeds.size());
    }

    private AppNotificationFeed feed(Long userId, Long viewerCount) {
        return AppNotificationFeed.createProfileTarget(
                userId,
                AppNotificationFeedType.TWIN_VIEW,
                viewerCount + "명이 회원님에게 관심을 보였어요.",
                "지난 " + INTERVAL_DAYS + "일간 " + viewerCount + "명이 회원님의 프로필 또는 대화 기록을 열람했어요.",
                userId,
                null);
    }

    private void sendPushes(List<AppNotificationFeed> feeds, List<Long> userIds) {
        Map<Long, List<Device>> devicesByUserId = pushRecipientResolver
                .resolve(userIds, NotificationType.EVENT).stream()
                .collect(Collectors.groupingBy(Device::getUserId));

        List<PushMessage> pushMessages = new ArrayList<>();
        for (AppNotificationFeed feed : feeds) {
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
                AppNotificationFeedTargetType.PROFILE,
                feed.getTargetUserId(),
                feed.getCreatedAt());
    }
}

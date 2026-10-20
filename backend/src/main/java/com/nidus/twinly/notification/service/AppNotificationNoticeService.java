package com.nidus.twinly.notification.service;

import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.dto.command.AppNotificationNoticePublishCommand;
import com.nidus.twinly.notification.dto.result.AppNotificationNoticePublishResult;
import com.nidus.twinly.notification.event.AppNotificationFeedCreatedEvent;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppNotificationNoticeService {

    private final AppNotificationFeedRepository appNotificationFeedRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AppNotificationNoticePublishResult publish(AppNotificationNoticePublishCommand command) {
        Long lastFeedId = appNotificationFeedRepository.findMaxId();

        int publishedCount = command.userIds() == null
                ? appNotificationFeedRepository.insertNoticeForActiveUsers(command.title(), command.body())
                : appNotificationFeedRepository.insertNoticeForActiveUsersIn(command.title(), command.body(), command.userIds());

        if (command.push() && publishedCount > 0) {
            eventPublisher.publishEvent(new AppNotificationFeedCreatedEvent(
                    appNotificationFeedRepository.findAllByIdGreaterThanAndTypeAndTitleAndBody(
                            lastFeedId, AppNotificationFeedType.NOTICE, command.title(), command.body())));
        }

        return new AppNotificationNoticePublishResult(publishedCount);
    }
}

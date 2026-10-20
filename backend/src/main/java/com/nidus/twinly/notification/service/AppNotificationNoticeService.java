package com.nidus.twinly.notification.service;

import com.nidus.twinly.notification.dto.command.AppNotificationNoticePublishCommand;
import com.nidus.twinly.notification.dto.result.AppNotificationNoticePublishResult;
import com.nidus.twinly.notification.repository.AppNotificationFeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppNotificationNoticeService {

    private final AppNotificationFeedRepository appNotificationFeedRepository;

    @Transactional
    public AppNotificationNoticePublishResult publish(AppNotificationNoticePublishCommand command) {
        int publishedCount = command.userIds() == null
                ? appNotificationFeedRepository.insertNoticeForActiveUsers(command.title(), command.body())
                : appNotificationFeedRepository.insertNoticeForActiveUsersIn(command.title(), command.body(), command.userIds());

        return new AppNotificationNoticePublishResult(publishedCount);
    }

    @Transactional(readOnly = true)
    public long countRecipients(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return appNotificationFeedRepository.countNoticeRecipients();
        }

        return appNotificationFeedRepository.countNoticeRecipientsIn(userIds);
    }
}

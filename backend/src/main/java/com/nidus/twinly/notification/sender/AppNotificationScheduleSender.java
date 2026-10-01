package com.nidus.twinly.notification.sender;

import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.event.OneTimePushEvent;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AppNotificationScheduleSender {

    private final AppNotificationScheduleRepository appNotificationScheduleRepository;
    private final AppNotificationFeedWriter appNotificationFeedWriter;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean send(Long scheduleId, Instant now) {
        Optional<AppNotificationSchedule> found = appNotificationScheduleRepository.findWithLockById(scheduleId);
        if (found.isEmpty() || found.get().getSentAt() != null) {
            return false;
        }

        AppNotificationSchedule schedule = found.get();

        switch (schedule.getType()) {
            case FRIEND -> appNotificationFeedWriter.writeFriend(
                    schedule.getUserId(), schedule.getPartnerUserId(), schedule.getSimulationDate());
            case FIRST_MEETING -> publishFirstMeeting(schedule, now);
        }

        schedule.markSent(now);

        return true;
    }

    private void publishFirstMeeting(AppNotificationSchedule schedule, Instant now) {
        Optional<User> partner = userRepository.findById(schedule.getPartnerUserId())
                .filter(user -> !user.isWithdrawn());
        if (partner.isEmpty()) {
            return;
        }

        String partnerName = partner.get().displayGivenName();

        eventPublisher.publishEvent(new OneTimePushEvent(
                schedule.getUserId(),
                partnerName + "님을 처음 만났어요.",
                "평행세계에서 " + partnerName + "님과 첫 대화를 시작했어요.",
                now));
    }
}

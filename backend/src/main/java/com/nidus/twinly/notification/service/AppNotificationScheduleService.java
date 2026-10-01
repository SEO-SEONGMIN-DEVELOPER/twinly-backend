package com.nidus.twinly.notification.service;

import com.nidus.twinly.common.logging.Actor;
import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.notification.entity.AppNotificationSchedule;
import com.nidus.twinly.notification.repository.AppNotificationScheduleRepository;
import com.nidus.twinly.notification.sender.AppNotificationScheduleSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppNotificationScheduleService {

    private static final String SCHEDULE_ID = "scheduleId";
    private static final String SCHEDULE_TYPE = "scheduleType";

    private final AppNotificationScheduleRepository appNotificationScheduleRepository;
    private final AppNotificationScheduleSender appNotificationScheduleSender;

    public int sendDue(Instant now) {
        List<AppNotificationSchedule> due = appNotificationScheduleRepository
                .findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(now);
        int sent = 0;

        for (AppNotificationSchedule schedule : due) {
            try {
                if (appNotificationScheduleSender.send(schedule.getId(), now)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                ErrorLog.error(log, null, Actor.user(schedule.getUserId()), e)
                        .addKeyValue(SCHEDULE_ID, schedule.getId())
                        .addKeyValue(SCHEDULE_TYPE, schedule.getType())
                        .log("예약 알림 발송에 실패했습니다");
            }
        }

        return sent;
    }
}

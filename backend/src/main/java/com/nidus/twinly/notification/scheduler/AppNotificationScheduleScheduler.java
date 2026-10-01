package com.nidus.twinly.notification.scheduler;

import com.nidus.twinly.notification.service.AppNotificationScheduleService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AppNotificationScheduleScheduler {

    private final AppNotificationScheduleService appNotificationScheduleService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "sendScheduledAppNotifications", lockAtMostFor = "PT5M")
    public void sendScheduled() {
        appNotificationScheduleService.sendDue(Instant.now());
    }
}

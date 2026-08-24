package com.nidus.twinly.people.scheduler;

import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.people.service.TwinViewNotificationService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TwinViewNotificationScheduler {

    private final TwinViewNotificationService twinViewNotificationService;

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "notifyTwinViewCounts", lockAtMostFor = "PT30M")
    public void notifyViewerCounts() {
        twinViewNotificationService.notifyViewerCounts(KstTimes.today());
    }
}

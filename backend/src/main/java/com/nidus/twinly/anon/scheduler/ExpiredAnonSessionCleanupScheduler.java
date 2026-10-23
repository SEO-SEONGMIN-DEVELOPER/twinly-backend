package com.nidus.twinly.anon.scheduler;

import com.nidus.twinly.anon.service.ExpiredAnonSessionCleanupService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredAnonSessionCleanupScheduler {

    private final ExpiredAnonSessionCleanupService expiredAnonSessionCleanupService;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "deleteExpiredAnonSessions", lockAtMostFor = "PT1H")
    public void deleteExpiredAnonSessions() {
        expiredAnonSessionCleanupService.deleteExpired();
    }
}

package com.nidus.twinly.user.scheduler;

import com.nidus.twinly.user.service.WithdrawnUserDeletionService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WithdrawnUserDeletionScheduler {

    private final WithdrawnUserDeletionService withdrawnUserDeletionService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "deleteWithdrawnUsers")
    public void deleteWithdrawnUsers() {
        withdrawnUserDeletionService.deleteAll();
    }
}

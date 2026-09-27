package com.nidus.twinly.purchase.scheduler;

import com.nidus.twinly.purchase.service.EarlySignupGrantService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EarlySignupGrantRetryScheduler {

    private final EarlySignupGrantService earlySignupGrantService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "retryEarlySignupGrants", lockAtMostFor = "PT5M")
    public void retryPending() {
        earlySignupGrantService.grantPending();
    }
}

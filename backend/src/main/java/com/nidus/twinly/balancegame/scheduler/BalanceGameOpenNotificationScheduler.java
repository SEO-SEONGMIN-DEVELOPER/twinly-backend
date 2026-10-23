package com.nidus.twinly.balancegame.scheduler;

import com.nidus.twinly.balancegame.service.BalanceGameOpenNotificationService;
import com.nidus.twinly.common.time.KstTimes;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BalanceGameOpenNotificationScheduler {

    private final BalanceGameOpenNotificationService balanceGameOpenNotificationService;

    @Scheduled(cron = "0 0 12,18 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "notifyBalanceGameOpened", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void notifyOpenedRound() {
        balanceGameOpenNotificationService.notifyOpenedRound(KstTimes.now());
    }
}

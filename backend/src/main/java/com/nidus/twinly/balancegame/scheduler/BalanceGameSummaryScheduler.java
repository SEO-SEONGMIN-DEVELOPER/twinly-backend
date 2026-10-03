package com.nidus.twinly.balancegame.scheduler;

import com.nidus.twinly.balancegame.service.BalanceGameSummaryService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class BalanceGameSummaryScheduler {

    private final BalanceGameSummaryService balanceGameSummaryService;

    @Scheduled(cron = "10 0 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "sendBalanceGameSummaries", lockAtMostFor = "PT5M")
    public void sendSummaries() {
        balanceGameSummaryService.sendEndedWithinLastHour(Instant.now());
    }
}

package com.nidus.twinly.season.scheduler;

import com.nidus.twinly.season.service.SeasonRecapService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SeasonRecapScheduler {

    private final SeasonRecapService seasonRecapService;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "prepareSeasonRecaps", lockAtMostFor = "PT30M")
    public void prepareRecaps() {
        seasonRecapService.prepare(Instant.now());
    }
}

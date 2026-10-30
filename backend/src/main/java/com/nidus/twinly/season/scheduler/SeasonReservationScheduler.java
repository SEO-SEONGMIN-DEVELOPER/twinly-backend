package com.nidus.twinly.season.scheduler;

import com.nidus.twinly.season.service.SeasonReservationService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SeasonReservationScheduler {

    private final SeasonReservationService seasonReservationService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "activateSeasonReservation", lockAtMostFor = "PT10M")
    public void activateDue() {
        seasonReservationService.activateDue(Instant.now());
    }
}

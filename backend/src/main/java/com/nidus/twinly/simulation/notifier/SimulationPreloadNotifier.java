package com.nidus.twinly.simulation.notifier;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.purchase.event.SimulationAccessGrantedEvent;
import com.nidus.twinly.simulation.client.SimulationPreloadClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationPreloadNotifier {

    private static final int PRELOAD_DAYS = 2;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    private final SimulationPreloadClient simulationPreloadClient;

    @Async("simulationPreloadTaskExecutor")
    @EventListener
    public void onSimulationAccessGranted(SimulationAccessGrantedEvent event) {
        LocalDateTime grantedAt = LocalDateTime.ofInstant(event.grantedAt(), KstTimes.ZONE).truncatedTo(ChronoUnit.SECONDS);
        List<LocalDate> dates = Stream.iterate(grantedAt.toLocalDate(), date -> date.plusDays(1)).limit(PRELOAD_DAYS).toList();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                simulationPreloadClient.preload(event.userId(), grantedAt, dates);
                log.info("시뮬레이션 선생성 요청 접수. userId={}, grantedAt={}, dates={}, attempt={}", event.userId(), grantedAt, dates, attempt);
                return;
            } catch (BusinessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    ErrorLog.error(log, ErrorCode.SIMULATION_PRELOAD_FAILED.name(), String.valueOf(event.userId()), e)
                            .log("시뮬레이션 선생성 요청이 재시도 후에도 실패했습니다. dates={}", dates);
                    return;
                }

                sleep(RETRY_DELAY);
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

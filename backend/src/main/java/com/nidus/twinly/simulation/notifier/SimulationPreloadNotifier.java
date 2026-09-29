package com.nidus.twinly.simulation.notifier;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.event.SeasonParticipationStartedEvent;
import com.nidus.twinly.simulation.client.SimulationPreloadClient;
import com.nidus.twinly.simulation.config.SimulationPreloadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationPreloadNotifier {

    private final SimulationPreloadClient simulationPreloadClient;
    private final SimulationPreloadProperties simulationPreloadProperties;

    @Async("simulationPreloadTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeasonParticipationStarted(SeasonParticipationStartedEvent event) {
        LocalDateTime participatedAt = LocalDateTime.ofInstant(event.participatedAt(), KstTimes.ZONE).truncatedTo(ChronoUnit.SECONDS);
        List<LocalDate> dates = Stream.iterate(participatedAt.toLocalDate(), date -> date.plusDays(1)).limit(simulationPreloadProperties.days()).toList();

        for (int attempt = 1; attempt <= simulationPreloadProperties.maxAttempts(); attempt++) {
            try {
                simulationPreloadClient.preload(event.userId(), participatedAt, dates);
                InfoLog.log(log, "시뮬레이션 선생성 요청을 접수했습니다.", field("userId", event.userId()), field("participatedAt", participatedAt), field("dates", dates), field("attempt", attempt));
                return;
            } catch (BusinessException e) {
                if (attempt == simulationPreloadProperties.maxAttempts()) {
                    ErrorLog.error(log, ErrorCode.SIMULATION_PRELOAD_FAILED.name(), String.valueOf(event.userId()), e)
                            .log("시뮬레이션 선생성 요청이 재시도 후에도 실패했습니다. dates={}", dates);
                    return;
                }

                sleep(simulationPreloadProperties.retryDelay());
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

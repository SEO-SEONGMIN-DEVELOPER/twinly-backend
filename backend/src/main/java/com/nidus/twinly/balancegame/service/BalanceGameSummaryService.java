package com.nidus.twinly.balancegame.service;

import com.nidus.twinly.balancegame.domain.BalanceGameSchedule;
import com.nidus.twinly.balancegame.entity.BalanceGameRound;
import com.nidus.twinly.balancegame.event.BalanceGameSummaryEvent;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository;
import com.nidus.twinly.balancegame.repository.BalanceGameAnswerRepository.MatchedCountProjection;
import com.nidus.twinly.balancegame.repository.BalanceGameRoundRepository;
import com.nidus.twinly.common.time.KstTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BalanceGameSummaryService {

    private final BalanceGameRoundRepository balanceGameRoundRepository;
    private final BalanceGameAnswerRepository balanceGameAnswerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void sendEndedRound(Instant now) {
        balanceGameRoundRepository
                .findByStartsAt(BalanceGameSchedule.previousRoundStartOf(LocalDateTime.ofInstant(now, KstTimes.ZONE)))
                .ifPresent(round -> send(round, now));
    }

    private void send(BalanceGameRound round, Instant now) {
        if (balanceGameRoundRepository.markSummarySent(round.getId(), now) == 0) {
            return;
        }

        Map<Long, Long> matchedCountByUserId = balanceGameAnswerRepository
                .countMatchesByUserInRound(round.getId(), KstTimes.now()).stream()
                .collect(Collectors.toMap(MatchedCountProjection::getUserId, MatchedCountProjection::getMatchedCount));

        if (matchedCountByUserId.isEmpty()) {
            return;
        }

        eventPublisher.publishEvent(new BalanceGameSummaryEvent(round.getId(), matchedCountByUserId, now));
    }
}

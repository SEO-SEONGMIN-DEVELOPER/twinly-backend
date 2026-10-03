package com.nidus.twinly.balancegame.service;

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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BalanceGameSummaryService {

    private static final Duration ROUND_DURATION = Duration.ofHours(1);

    private final BalanceGameRoundRepository balanceGameRoundRepository;
    private final BalanceGameAnswerRepository balanceGameAnswerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void sendEndedWithinLastHour(Instant now) {
        balanceGameRoundRepository
                .findAllBySummarySentAtIsNullAndStartsAtBetween(now.minus(ROUND_DURATION.multipliedBy(2)), now.minus(ROUND_DURATION))
                .forEach(round -> send(round, now));
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

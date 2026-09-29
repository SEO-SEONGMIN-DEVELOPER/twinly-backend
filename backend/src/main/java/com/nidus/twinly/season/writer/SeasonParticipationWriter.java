package com.nidus.twinly.season.writer;

import com.nidus.twinly.common.logging.InfoLog;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.season.event.SeasonParticipationStartedEvent;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static com.nidus.twinly.common.logging.LogField.field;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonParticipationWriter {

    private final CurrentSeasonReader currentSeasonReader;
    private final EntitlementReader entitlementReader;
    private final ConsentReader consentReader;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void participateInCurrentSeasonIfEligible(Long userId) {
        if (!entitlementReader.hasSimulationAccess(userId)
                || !consentReader.hasAgreedAllRequired(userId, PolicyKind.PARALLEL_ENTRY)) {
            return;
        }

        participateInCurrentSeason(userId);
    }

    @Transactional
    public void participateInCurrentSeason(Long userId) {
        Long seasonId = currentSeasonReader.read().getId();
        boolean participated = seasonParticipationRepository.existsByUserIdAndSeasonId(userId, seasonId);

        seasonParticipationRepository.upsert(userId, seasonId);

        if (!participated) {
            eventPublisher.publishEvent(new SeasonParticipationStartedEvent(userId, Instant.now()));
        }
    }

    @Transactional
    public void participateAllEligible(Long seasonId) {
        List<Long> userIds = consentReader.filterAgreedAllRequired(entitlementReader.userIdsWithSimulationAccess(), PolicyKind.PARALLEL_ENTRY);

        userIds.forEach(userId -> seasonParticipationRepository.upsert(userId, seasonId));

        InfoLog.log(log, "시즌 전환에 따라 결제·필수 약관 동의 유저를 자동 참가시켰습니다.", field("seasonId", seasonId), field("count", userIds.size()));
    }
}

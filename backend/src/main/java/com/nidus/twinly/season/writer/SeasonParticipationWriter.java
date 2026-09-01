package com.nidus.twinly.season.writer;

import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonParticipationWriter {

    private final CurrentSeasonReader currentSeasonReader;
    private final EntitlementReader entitlementReader;
    private final SeasonParticipationRepository seasonParticipationRepository;

    @Transactional
    public void participateInCurrentSeason(Long userId) {
        seasonParticipationRepository.upsert(userId, currentSeasonReader.read().getId());
    }

    @Transactional
    public void participateAllWithSimulationAccess(Long seasonId) {
        List<Long> userIds = entitlementReader.userIdsWithSimulationAccess();

        userIds.forEach(userId -> seasonParticipationRepository.upsert(userId, seasonId));

        log.info("시즌 전환에 따라 결제 유저를 자동 참가시켰습니다. seasonId={}, count={}", seasonId, userIds.size());
    }
}

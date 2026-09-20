package com.nidus.twinly.season.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.result.SeasonChangeResult;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.event.SeasonChangedEvent;
import com.nidus.twinly.purchase.service.PurchaseService;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.season.writer.SeasonParticipationWriter;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.reader.ConsentReader;
import com.nidus.twinly.purchase.reader.EntitlementReader;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonService {

    private final CurrentSeasonReader currentSeasonReader;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonParticipationWriter seasonParticipationWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final PurchaseService purchaseService;
    private final EntitlementReader entitlementReader;
    private final ConsentReader consentReader;
    private final UserRepository userRepository;

    @Transactional
    public SeasonChangeResult changeSeason(SeasonChangeCommand command) {
        if (!command.startedAt().isBefore(command.endedAt())) {
            throw new BusinessException(ErrorCode.INVALID_SEASON_PERIOD);
        }

        seasonRepository.findAllByIsActiveTrue().forEach(Season::deactivate);

        Season season = seasonRepository.save(Season.create(command.startedAt(), command.endedAt()));

        seasonParticipationWriter.participateAllEligible(season.getId());

        eventPublisher.publishEvent(new SeasonChangedEvent(season.getId()));

        return new SeasonChangeResult(season.getId(), season.getStartedAt(), season.getEndedAt());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public void participateIn(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        purchaseService.syncQuietly(user);

        if (!entitlementReader.hasSimulationAccess(userId)) {
            throw new BusinessException(ErrorCode.SIMULATION_ACCESS_REQUIRED);
        }
        if (!consentReader.hasAgreedAllRequired(userId, PolicyKind.PARALLEL_ENTRY)) {
            throw new BusinessException(ErrorCode.SIMULATION_CONSENT_REQUIRED);
        }

        seasonParticipationWriter.participateInCurrentSeason(userId);
    }

    public SeasonParticipationResult participation(Long userId) {
        userRepository.findById(userId).ifPresent(purchaseService::syncIfStale);

        Long currentSeasonId = currentSeasonReader.read().getId();

        Instant participatedInAt = seasonParticipationRepository.findByUserIdAndSeasonId(userId, currentSeasonId)
                .map(SeasonParticipation::getParticipatedInAt)
                .orElse(null);

        return new SeasonParticipationResult(currentSeasonId, participatedInAt);
    }
}

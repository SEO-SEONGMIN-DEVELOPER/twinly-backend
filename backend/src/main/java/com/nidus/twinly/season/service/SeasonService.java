package com.nidus.twinly.season.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.result.SeasonChangeResult;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.event.SeasonChangedEvent;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonService {

    private final CurrentSeasonReader currentSeasonReader;
    private final SeasonParticipationRepository seasonParticipationRepository;
    private final SeasonRepository seasonRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SeasonChangeResult changeSeason(SeasonChangeCommand command) {
        if (!command.startedAt().isBefore(command.endedAt())) {
            throw new BusinessException(ErrorCode.INVALID_SEASON_PERIOD);
        }

        seasonRepository.findAllByIsActiveTrue().forEach(Season::deactivate);

        Season season = seasonRepository.save(Season.create(command.startedAt(), command.endedAt()));

        eventPublisher.publishEvent(new SeasonChangedEvent(season.getId()));

        return new SeasonChangeResult(season.getId(), season.getStartedAt(), season.getEndedAt());
    }

    @Transactional
    public void participateIn(Long userId) {
        Season season = currentSeasonReader.read();

        Instant now = Instant.now();

        if (now.isBefore(season.getStartedAt()) || now.isAfter(season.getEndedAt())) {
            throw new BusinessException(ErrorCode.SEASON_NOT_JOINABLE);
        }

        seasonParticipationRepository.upsert(userId, season.getId());
    }

    public SeasonParticipationResult participation(Long userId) {
        Long currentSeasonId = currentSeasonReader.read().getId();

        Instant participatedInAt = seasonParticipationRepository.findByUserIdAndSeasonId(userId, currentSeasonId)
                .map(SeasonParticipation::getParticipatedInAt)
                .orElse(null);

        return new SeasonParticipationResult(currentSeasonId, participatedInAt);
    }
}

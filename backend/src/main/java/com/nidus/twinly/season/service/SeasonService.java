package com.nidus.twinly.season.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.reader.CurrentSeasonReader;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SeasonService {

    private final CurrentSeasonReader currentSeasonReader;
    private final SeasonParticipationRepository seasonParticipationRepository;

    @Transactional
    public void participateIn(Long userId) {
        Season season = currentSeasonReader.read();

        Instant now = Instant.now();

        if (now.isBefore(season.getStartedAt()) || now.isAfter(season.getEndedAt())) {
            throw new BusinessException(ErrorCode.SEASON_NOT_JOINABLE);
        }

        if (seasonParticipationRepository.existsByUserIdAndSeasonId(userId, season.getId())) {
            return;
        }

        seasonParticipationRepository.save(SeasonParticipation.create(userId, season.getId()));
    }

    public SeasonParticipationResult participation(Long userId) {
        Long currentSeasonId = currentSeasonReader.read().getId();

        Instant participatedInAt = seasonParticipationRepository.findByUserIdAndSeasonId(userId, currentSeasonId)
                .map(SeasonParticipation::getParticipatedInAt)
                .orElse(null);

        return new SeasonParticipationResult(currentSeasonId, participatedInAt);
    }
}

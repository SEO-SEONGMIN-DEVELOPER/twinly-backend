package com.nidus.twinly.season.service;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.season.dto.result.SeasonParticipationResult;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SeasonService {

    @Value("${app.current-season-id}")
    private Long currentSeasonId;

    private final SeasonRepository seasonRepository;
    private final SeasonParticipationRepository seasonParticipationRepository;

    @Transactional
    public void participateIn(Long userId) {
        Season season = seasonRepository.findById(currentSeasonId)
                .orElseThrow(() -> new IllegalStateException("현재 시즌으로 설정된 시즌이 존재하지 않습니다: seasonId=" + currentSeasonId));

        Instant now = Instant.now();

        if (now.isBefore(season.getStartedAt()) || now.isAfter(season.getEndedAt())) {
            throw new BusinessException(ErrorCode.SEASON_NOT_JOINABLE);
        }

        if (seasonParticipationRepository.existsByUserIdAndSeasonId(userId, currentSeasonId)) {
            return;
        }

        seasonParticipationRepository.save(SeasonParticipation.create(userId, currentSeasonId));
    }

    public SeasonParticipationResult participation(Long userId) {
        Instant participatedInAt = seasonParticipationRepository.findByUserIdAndSeasonId(userId, currentSeasonId)
                .map(SeasonParticipation::getParticipatedInAt)
                .orElse(null);

        return new SeasonParticipationResult(currentSeasonId, participatedInAt);
    }
}

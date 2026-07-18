package com.nidus.twinly.season.repository;

import com.nidus.twinly.season.entity.SeasonParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeasonParticipationRepository extends JpaRepository<SeasonParticipation, Long> {

    boolean existsByUserIdAndSeasonId(Long userId, Long seasonId);

    Optional<SeasonParticipation> findByUserIdAndSeasonId(Long userId, Long seasonId);
}

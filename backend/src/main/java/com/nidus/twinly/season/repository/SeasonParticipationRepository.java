package com.nidus.twinly.season.repository;

import com.nidus.twinly.season.entity.SeasonParticipation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeasonParticipationRepository extends JpaRepository<SeasonParticipation, Long> {

    boolean existsByUserIdAndSeasonId(Long userId, Long seasonId);

    Optional<SeasonParticipation> findByUserIdAndSeasonId(Long userId, Long seasonId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO season_participations (user_id, season_id, participated_in_at, created_at)
            VALUES (:userId, :seasonId, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("seasonId") Long seasonId);
}

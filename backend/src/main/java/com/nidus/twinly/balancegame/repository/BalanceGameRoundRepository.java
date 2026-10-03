package com.nidus.twinly.balancegame.repository;

import com.nidus.twinly.balancegame.entity.BalanceGameRound;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BalanceGameRoundRepository extends JpaRepository<BalanceGameRound, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO balance_game_rounds (starts_at, question_id, created_at)
            VALUES (:startsAt, :questionId, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void upsert(@Param("startsAt") Instant startsAt, @Param("questionId") Long questionId);

    Optional<BalanceGameRound> findByStartsAt(Instant startsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BalanceGameRound r WHERE r.id = :id")
    Optional<BalanceGameRound> findByIdForUpdate(@Param("id") Long id);

    List<BalanceGameRound> findAllBySummarySentAtIsNullAndStartsAtBetween(Instant from, Instant to);

    @Modifying
    @Query("UPDATE BalanceGameRound r SET r.summarySentAt = :now WHERE r.id = :id AND r.summarySentAt IS NULL")
    int markSummarySent(@Param("id") Long id, @Param("now") Instant now);
}

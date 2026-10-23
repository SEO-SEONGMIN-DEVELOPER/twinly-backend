package com.nidus.twinly.anon.repository;

import com.nidus.twinly.anon.entity.AnonSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnonSessionRepository extends JpaRepository<AnonSession, Long> {

    Optional<AnonSession> findByToken(UUID token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AnonSession> findWithLockById(Long id);

    @Query("""
            SELECT s.id FROM AnonSession s
            WHERE s.expiresAt < :now
              AND s.id > :afterId
            ORDER BY s.id ASC
            """)
    List<Long> findExpiredIds(@Param("now") Instant now, @Param("afterId") Long afterId, Pageable pageable);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
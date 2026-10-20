package com.nidus.twinly.anon.repository;

import com.nidus.twinly.anon.entity.AnonSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AnonSessionRepository extends JpaRepository<AnonSession, Long> {

    Optional<AnonSession> findByToken(UUID token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AnonSession> findWithLockById(Long id);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
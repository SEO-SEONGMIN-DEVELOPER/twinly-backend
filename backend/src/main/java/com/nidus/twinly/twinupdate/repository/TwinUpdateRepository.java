package com.nidus.twinly.twinupdate.repository;

import com.nidus.twinly.twinupdate.domain.TwinUpdateStatus;
import com.nidus.twinly.twinupdate.entity.TwinUpdate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface TwinUpdateRepository extends JpaRepository<TwinUpdate, Long> {

    Optional<TwinUpdate> findByUpdateId(UUID updateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TwinUpdate> findWithLockByUpdateId(UUID updateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TwinUpdate> findWithLockByUserIdAndStatus(Long userId, TwinUpdateStatus status);

    Optional<TwinUpdate> findFirstByUserIdAndStatusOrderByCompletedAtDesc(Long userId, TwinUpdateStatus status);
}

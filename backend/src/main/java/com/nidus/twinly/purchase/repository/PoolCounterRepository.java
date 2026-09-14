package com.nidus.twinly.purchase.repository;

import com.nidus.twinly.purchase.entity.PoolCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PoolCounterRepository extends JpaRepository<PoolCounter, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PoolCounter> findWithLockById(Integer id);
}

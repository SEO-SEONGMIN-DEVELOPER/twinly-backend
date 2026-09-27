package com.nidus.twinly.purchase.repository;

import com.nidus.twinly.purchase.entity.EarlySignupGrantCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface EarlySignupGrantCounterRepository extends JpaRepository<EarlySignupGrantCounter, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EarlySignupGrantCounter> findWithLockById(Integer id);
}

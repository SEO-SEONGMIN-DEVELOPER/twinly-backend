package com.nidus.twinly.purchase.repository;

import com.nidus.twinly.purchase.entity.RevenueCatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RevenueCatEventRepository extends JpaRepository<RevenueCatEvent, Long> {

    Optional<RevenueCatEvent> findByEventId(String eventId);
}

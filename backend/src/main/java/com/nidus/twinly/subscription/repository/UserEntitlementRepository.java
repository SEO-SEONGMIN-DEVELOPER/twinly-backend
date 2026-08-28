package com.nidus.twinly.subscription.repository;

import com.nidus.twinly.subscription.entity.UserEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {

    List<UserEntitlement> findAllByUserId(Long userId);
}

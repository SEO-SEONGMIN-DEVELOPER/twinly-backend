package com.nidus.twinly.purchase.repository;

import com.nidus.twinly.purchase.entity.UserEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {

    List<UserEntitlement> findAllByUserId(Long userId);
}

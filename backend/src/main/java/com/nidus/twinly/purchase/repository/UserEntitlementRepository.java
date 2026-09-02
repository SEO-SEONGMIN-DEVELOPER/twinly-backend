package com.nidus.twinly.purchase.repository;

import com.nidus.twinly.purchase.entity.UserEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {

    List<UserEntitlement> findAllByUserId(Long userId);

    List<UserEntitlement> findAllByUserIdInAndEntitlement(List<Long> userIds, String entitlement);

    @Query("""
            SELECT COUNT(e) > 0 FROM UserEntitlement e
            WHERE e.userId = :userId
              AND e.entitlement = :entitlement
              AND (e.expiresAt IS NULL OR e.expiresAt > :now)
            """)
    boolean existsActive(@Param("userId") Long userId,
                         @Param("entitlement") String entitlement,
                         @Param("now") Instant now);

    @Query("""
            SELECT e.userId FROM UserEntitlement e
            WHERE e.entitlement = :entitlement
              AND (e.expiresAt IS NULL OR e.expiresAt > :now)
            """)
    List<Long> findUserIdsActive(@Param("entitlement") String entitlement,
                                 @Param("now") Instant now);
}

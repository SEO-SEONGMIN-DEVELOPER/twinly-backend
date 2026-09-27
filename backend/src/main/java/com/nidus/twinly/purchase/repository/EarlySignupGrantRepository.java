package com.nidus.twinly.purchase.repository;

import com.nidus.twinly.purchase.entity.EarlySignupGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EarlySignupGrantRepository extends JpaRepository<EarlySignupGrant, Long> {

    boolean existsByDiHash(String diHash);

    Optional<EarlySignupGrant> findByUserIdAndGrantedAtIsNull(Long userId);

    List<EarlySignupGrant> findAllByGrantedAtIsNullAndExpiresAtAfter(Instant now);

    @Modifying
    @Query("UPDATE EarlySignupGrant g SET g.grantedAt = :grantedAt WHERE g.id = :id AND g.grantedAt IS NULL")
    int markGranted(@Param("id") Long id, @Param("grantedAt") Instant grantedAt);
}

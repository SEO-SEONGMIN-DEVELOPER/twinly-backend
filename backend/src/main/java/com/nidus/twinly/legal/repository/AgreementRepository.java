package com.nidus.twinly.legal.repository;

import com.nidus.twinly.legal.entity.Agreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgreementRepository extends JpaRepository<Agreement, Long> {

    List<Agreement> findAllByUserIdAndRevokedAtIsNull(Long userId);

    @Modifying
    @Query(value = """
            UPDATE agreements
            SET revoked_at = UTC_TIMESTAMP(6)
            WHERE user_id = :userId
              AND revoked_at IS NULL
              AND policy_id IN (
                  SELECT older.id
                  FROM policies older
                  JOIN policies target ON target.policy_name_id = older.policy_name_id
                  WHERE target.id IN (:policyIds)
                    AND older.version <= target.version
              )
            """, nativeQuery = true)
    void revokeWithPreviousVersionsByUserIdAndPolicyIdIn(@Param("userId") Long userId,
                                                         @Param("policyIds") List<Long> policyIds);
}

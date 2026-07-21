package com.nidus.twinly.anon.repository;

import com.nidus.twinly.anon.entity.AnonSessionAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnonSessionAgreementRepository extends JpaRepository<AnonSessionAgreement, Long> {

    List<AnonSessionAgreement> findAllByAnonSessionId(Long anonSessionId);

    List<AnonSessionAgreement> findAllByAnonSessionIdAndRevokedAtIsNull(Long anonSessionId);

    @Modifying
    @Query(value = """
            UPDATE anon_session_agreements
            SET revoked_at = now()
            WHERE anon_session_id = :anonSessionId
              AND revoked_at IS NULL
              AND policy_id IN (
                  SELECT older.id
                  FROM policies older
                  JOIN policies target ON target.policy_name_id = older.policy_name_id
                  WHERE target.id IN (:policyIds)
                    AND older.version <= target.version
              )
            """, nativeQuery = true)
    void revokeWithPreviousVersionsByAnonSessionIdAndPolicyIdIn(@Param("anonSessionId") Long anonSessionId,
                                                                @Param("policyIds") List<Long> policyIds);
}

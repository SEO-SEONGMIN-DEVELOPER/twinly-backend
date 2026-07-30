package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.entity.DisclosureAgreement;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisclosureAgreementRepository extends JpaRepository<DisclosureAgreement, Long> {

    List<DisclosureAgreement> findAllByUserId(Long userId);

    boolean existsByUserIdAndField(Long userId, DisclosureField field);

    long deleteByUserIdAndField(Long userId, DisclosureField field);

    @Modifying
    @Query(value = """
            INSERT INTO disclosure_agreements (user_id, field, agreed_at, created_at)
            VALUES (:userId, :field, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("field") String field);
}

package com.nidus.twinly.relationship.repository;

import com.nidus.twinly.relationship.entity.Relationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RelationshipRepository extends JpaRepository<Relationship, Long> {

    @Query(value = """
        SELECT r.*
        FROM relationships r
        WHERE r.user_id = :userId AND r.partner_id = :partnerId
        ORDER BY r.date DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Relationship> findLatestByUserIdAndPartnerId(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    @Query(value = """
            SELECT r.*
            FROM relationships r
            INNER JOIN (
                SELECT partner_id, MAX(date) AS max_date
                FROM relationships
                WHERE user_id = :userId AND partner_id IN (:partnerIds)
                GROUP BY partner_id
            ) latest ON r.user_id = :userId 
                         AND r.partner_id = latest.partner_id 
                         AND r.date = latest.max_date
            """, nativeQuery = true)
    List<Relationship> findLatestByUserIdAndPartnerIdIn(@Param("userId") Long userId, @Param("partnerIds") List<Long> partnerIds);
}

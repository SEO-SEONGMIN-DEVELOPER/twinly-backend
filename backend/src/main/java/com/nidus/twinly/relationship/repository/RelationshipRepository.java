package com.nidus.twinly.relationship.repository;

import com.nidus.twinly.relationship.entity.Relationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RelationshipRepository extends JpaRepository<Relationship, Long> {

    @Query(value = """
        SELECT r.*
        FROM relationships r
        WHERE r.user_id = :userId AND r.partner_user_id = :partnerUserId
        ORDER BY r.date DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Relationship> findLatestByUserIdAndPartnerUserId(@Param("userId") Long userId, @Param("partnerUserId") Long partnerUserId);

    @Query(value = """
            SELECT r.*
            FROM relationships r
            INNER JOIN (
                SELECT partner_user_id, MAX(date) AS max_date
                FROM relationships
                WHERE user_id = :userId AND partner_user_id IN (:partnerUserIds)
                GROUP BY partner_user_id
            ) latest ON r.user_id = :userId
                         AND r.partner_user_id = latest.partner_user_id
                         AND r.date = latest.max_date
            """, nativeQuery = true)
    List<Relationship> findLatestByUserIdAndPartnerUserIdIn(@Param("userId") Long userId, @Param("partnerUserIds") List<Long> partnerUserIds);

    @Query(value = """
            SELECT DISTINCT partner_user_id
            FROM relationships
            WHERE user_id = :userId
              AND (:cursor IS NULL OR partner_user_id > :cursor)
            ORDER BY partner_user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findPartnerUserIdsByUserId(@Param("userId") Long userId,
                                          @Param("cursor") Long cursor,
                                          @Param("limit") Integer limit);

    List<Relationship> findAllByUserIdAndPartnerUserIdAndDateBetweenOrderByDateAsc(Long userId, Long partnerUserId, LocalDate from, LocalDate to);

    List<Relationship> findAllByUserIdAndPartnerUserIdOrderByDateAsc(Long userId, Long partnerUserId);
}

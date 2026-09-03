package com.nidus.twinly.relationship.repository;

import com.nidus.twinly.relationship.entity.Relationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
        WHERE r.user_id = :userId AND r.partner_user_id = :partnerUserId AND r.date < :date
        ORDER BY r.date DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Relationship> findLatestByUserIdAndPartnerUserIdBeforeDate(@Param("userId") Long userId,
                                                                       @Param("partnerUserId") Long partnerUserId,
                                                                       @Param("date") LocalDate date);

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
            SELECT DISTINCT r.partner_user_id
            FROM relationships r
            WHERE r.user_id = :userId
              AND (:cursor IS NULL OR r.partner_user_id > :cursor)
              AND NOT EXISTS (
                  SELECT 1
                  FROM blocks b
                  WHERE b.user_id = :userId
                    AND b.blocked_user_id = r.partner_user_id
              )
            ORDER BY r.partner_user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findPartnerUserIdsByUserId(@Param("userId") Long userId,
                                          @Param("cursor") Long cursor,
                                          @Param("limit") Integer limit);

    List<Relationship> findAllByUserIdAndPartnerUserIdOrderByDateAsc(Long userId, Long partnerUserId);

    @Modifying
    @Query("DELETE FROM Relationship r WHERE r.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);

    void deleteAllByUserIdAndDate(Long userId, LocalDate date);

    @Query("""
            SELECT r FROM Relationship r
            WHERE r.userId = :userId AND r.partnerUserId = :partnerUserId
              AND r.date <= :to
              AND (r.date >= :from OR r.date = (
                    SELECT MAX(p.date) FROM Relationship p
                    WHERE p.userId = :userId AND p.partnerUserId = :partnerUserId AND p.date < :from))
            ORDER BY r.date ASC
            """)
    List<Relationship> findForDeltaRange(@Param("userId") Long userId,
                                         @Param("partnerUserId") Long partnerUserId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
}

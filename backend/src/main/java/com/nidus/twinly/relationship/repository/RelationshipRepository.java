package com.nidus.twinly.relationship.repository;

import com.nidus.twinly.relationship.entity.Relationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RelationshipRepository extends JpaRepository<Relationship, Long> {

    @Query(value = """
        SELECT r.*
        FROM relationships r
        WHERE r.user_id = :userId AND r.partner_user_id = :partnerUserId AND r.update_time <= :now
        ORDER BY r.date DESC, r.update_time DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Relationship> findLatestUntilByUserIdAndPartnerUserId(@Param("userId") Long userId,
                                                                 @Param("partnerUserId") Long partnerUserId,
                                                                 @Param("now") LocalDateTime now);

    @Query(value = """
        SELECT r.*
        FROM relationships r
        WHERE r.user_id = :userId AND r.partner_user_id = :partnerUserId AND r.date < :date
        ORDER BY r.date DESC, r.update_time DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Relationship> findLatestByUserIdAndPartnerUserIdBeforeDate(@Param("userId") Long userId,
                                                                       @Param("partnerUserId") Long partnerUserId,
                                                                       @Param("date") LocalDate date);

    @Query(value = """
            SELECT id, user_id, date, version, partner_user_id, intimacy, partner_model, update_time, intimacy_as_of, created_at
            FROM (
                SELECT r.*,
                       ROW_NUMBER() OVER (PARTITION BY r.partner_user_id ORDER BY r.date DESC, r.update_time DESC) AS row_num
                FROM relationships r
                WHERE r.user_id = :userId AND r.partner_user_id IN (:partnerUserIds) AND r.update_time <= :now
            ) latest
            WHERE latest.row_num = 1
            """, nativeQuery = true)
    List<Relationship> findLatestUntilByUserIdAndPartnerUserIdIn(@Param("userId") Long userId,
                                                               @Param("partnerUserIds") List<Long> partnerUserIds,
                                                               @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT latest.*
            FROM (
                SELECT r.*,
                       ROW_NUMBER() OVER (PARTITION BY r.partner_user_id ORDER BY r.date DESC, r.update_time DESC) AS row_num
                FROM relationships r
                WHERE r.user_id = :userId AND r.date < :date
            ) latest
            WHERE latest.row_num = 1
            """, nativeQuery = true)
    List<Relationship> findLatestBeforeDateByUserId(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query(value = """
            SELECT DISTINCT r.partner_user_id
            FROM relationships r
            WHERE r.user_id = :userId
              AND r.update_time <= :now
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
                                          @Param("limit") Integer limit,
                                          @Param("now") LocalDateTime now);

    List<Relationship> findAllByUserIdAndPartnerUserIdAndUpdateTimeLessThanEqualOrderByDateAscUpdateTimeAsc(Long userId, Long partnerUserId, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM Relationship r WHERE r.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);

    void deleteAllByUserIdAndDate(Long userId, LocalDate date);

    @Query("""
            SELECT r FROM Relationship r
            WHERE r.userId = :userId AND r.partnerUserId = :partnerUserId
              AND r.date <= :to
              AND r.updateTime <= :now
              AND (r.date >= :from OR r.date = (
                    SELECT MAX(p.date) FROM Relationship p
                    WHERE p.userId = :userId AND p.partnerUserId = :partnerUserId AND p.date < :from AND p.updateTime <= :now))
            ORDER BY r.date ASC, r.updateTime ASC
            """)
    List<Relationship> findForDeltaRange(@Param("userId") Long userId,
                                         @Param("partnerUserId") Long partnerUserId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("now") LocalDateTime now);
}

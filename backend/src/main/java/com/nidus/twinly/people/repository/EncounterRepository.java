package com.nidus.twinly.people.repository;

import com.nidus.twinly.people.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    Optional<Encounter> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @Modifying
    @Query(value = """
            INSERT INTO encounters (user_a_id, user_b_id, created_at)
            VALUES (:userAId, :userBId, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void upsert(@Param("userAId") Long userAId, @Param("userBId") Long userBId);

    @Query(value = """
            SELECT e.*
            FROM encounters e
            WHERE (e.user_a_id = :userId AND e.user_b_id IN (:partnerUserIds))
               OR (e.user_b_id = :userId AND e.user_a_id IN (:partnerUserIds))
            """, nativeQuery = true)
    List<Encounter> findAllByUserIdAndPartnerUserIdIn(@Param("userId") Long userId,
                                                      @Param("partnerUserIds") List<Long> partnerUserIds);
}

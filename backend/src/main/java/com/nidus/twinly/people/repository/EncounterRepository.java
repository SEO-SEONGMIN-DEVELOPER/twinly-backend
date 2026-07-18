package com.nidus.twinly.people.repository;

import com.nidus.twinly.people.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    @Query(value = """
            SELECT e.*
            FROM encounters e
            WHERE (e.user_a_id = :userId AND e.user_b_id IN (:partnerUserIds))
               OR (e.user_b_id = :userId AND e.user_a_id IN (:partnerUserIds))
            """, nativeQuery = true)
    List<Encounter> findAllByUserIdAndPartnerUserIdIn(@Param("userId") Long userId,
                                                      @Param("partnerUserIds") List<Long> partnerUserIds);
}

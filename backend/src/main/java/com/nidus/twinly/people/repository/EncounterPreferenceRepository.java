package com.nidus.twinly.people.repository;

import com.nidus.twinly.people.entity.EncounterPreference;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EncounterPreferenceRepository extends JpaRepository<EncounterPreference, Long> {

    List<EncounterPreference> findAllByEncounterIdInAndUserId(List<Long> encounterIds, Long userId);

    Optional<EncounterPreference> findByEncounterIdAndUserId(Long encounterId, Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO encounter_preferences (encounter_id, user_id, is_favorited, created_at)
            VALUES (:encounterId, :userId, :isFavorited, UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE is_favorited = :isFavorited
            """, nativeQuery = true)
    void upsertIsFavorited(@Param("encounterId") Long encounterId, @Param("userId") Long userId,
                           @Param("isFavorited") boolean isFavorited);
}

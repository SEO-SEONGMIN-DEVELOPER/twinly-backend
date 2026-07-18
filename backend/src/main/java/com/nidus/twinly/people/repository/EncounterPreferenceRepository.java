package com.nidus.twinly.people.repository;

import com.nidus.twinly.people.entity.EncounterPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncounterPreferenceRepository extends JpaRepository<EncounterPreference, Long> {

    List<EncounterPreference> findAllByEncounterIdInAndUserId(List<Long> encounterIds, Long userId);
}

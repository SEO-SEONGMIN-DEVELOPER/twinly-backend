package com.nidus.twinly.people.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "encounter_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncounterPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long encounterId;

    private Long userId;

    private Boolean isFavorited;

    private Instant createdAt;

    public static EncounterPreference create(Long encounterId, Long userId) {
        EncounterPreference preference = new EncounterPreference();
        preference.encounterId = encounterId;
        preference.userId = userId;
        preference.isFavorited = false;
        preference.createdAt = Instant.now();
        return preference;
    }
}

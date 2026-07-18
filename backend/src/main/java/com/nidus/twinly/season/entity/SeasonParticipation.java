package com.nidus.twinly.season.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "season_participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long seasonId;

    private Instant participatedInAt;

    private Instant createdAt;

    public static SeasonParticipation create(Long userId, Long seasonId) {
        SeasonParticipation participation = new SeasonParticipation();

        participation.userId = userId;
        participation.seasonId = seasonId;
        participation.participatedInAt = Instant.now();
        participation.createdAt = Instant.now();

        return participation;
    }
}
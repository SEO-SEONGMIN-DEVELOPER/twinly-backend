package com.nidus.twinly.season.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "seasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant startedAt;

    private Instant endedAt;

    private Boolean isActive;

    private Instant createdAt;

    private Instant recapCreatedAt;

    private Instant recapNotifiedAt;

    public static Season create(Instant startedAt, Instant endedAt) {
        Season season = new Season();

        season.startedAt = startedAt;
        season.endedAt = endedAt;
        season.isActive = true;
        season.createdAt = Instant.now();

        return season;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void markRecapCreated(Instant createdAt) {
        this.recapCreatedAt = createdAt;
    }

    public void markRecapNotified(Instant notifiedAt) {
        this.recapNotifiedAt = notifiedAt;
    }
}
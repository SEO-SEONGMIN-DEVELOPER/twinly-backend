package com.nidus.twinly.season.entity;

import com.nidus.twinly.common.time.KstTimes;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    private Instant activatedAt;

    private Instant poolsReassignedAt;

    public static Season create(Instant startedAt, Instant endedAt) {
        Season season = new Season();

        season.startedAt = startedAt;
        season.endedAt = endedAt;
        season.isActive = true;
        season.createdAt = Instant.now();
        season.activatedAt = season.createdAt;

        return season;
    }

    public static Season reserve(Instant startedAt, Instant endedAt) {
        Season season = new Season();

        season.startedAt = startedAt;
        season.endedAt = endedAt;
        season.isActive = false;
        season.createdAt = Instant.now();

        return season;
    }

    public void activate(Instant activatedAt) {
        this.isActive = true;
        this.activatedAt = activatedAt;
    }

    public void markPoolsReassigned(Instant reassignedAt) {
        this.poolsReassignedAt = reassignedAt;
    }

    public LocalDate startDate() {
        return KstTimes.toLocalDateTime(startedAt).toLocalDate();
    }

    public LocalDate nextStartDate() {
        LocalDateTime ended = KstTimes.toLocalDateTime(endedAt);

        return ended.toLocalTime().equals(LocalTime.MIDNIGHT) ? ended.toLocalDate() : ended.toLocalDate().plusDays(1);
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
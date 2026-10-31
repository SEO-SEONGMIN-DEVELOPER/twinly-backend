package com.nidus.twinly.relationship.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@DynamicUpdate
@Table(name = "relationships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Relationship {

    public static final String STARTER_VERSION = "starter";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private LocalDate date;

    @Column(columnDefinition = "TEXT")
    private String version;

    private Long partnerUserId;

    private Integer intimacy;

    @Column(columnDefinition = "TEXT")
    private String partnerModel;

    private LocalDateTime updateTime;

    private Instant intimacyAsOf;

    private Instant createdAt;

    private Long seasonId;

    public static Relationship create(Long userId, LocalDate date, String version, Long partnerUserId, Integer intimacy, String partnerModel, LocalDateTime updateTime, Instant intimacyAsOf) {
        return create(userId, date, version, partnerUserId, intimacy, partnerModel, updateTime, intimacyAsOf, null);
    }

    public static Relationship create(Long userId, LocalDate date, String version, Long partnerUserId, Integer intimacy, String partnerModel, LocalDateTime updateTime, Instant intimacyAsOf, Long seasonId) {
        Relationship relationship = new Relationship();

        relationship.userId = userId;
        relationship.date = date;
        relationship.version = version;
        relationship.partnerUserId = partnerUserId;
        relationship.intimacy = intimacy;
        relationship.partnerModel = partnerModel;
        relationship.updateTime = updateTime;
        relationship.intimacyAsOf = intimacyAsOf;
        relationship.createdAt = Instant.now();
        relationship.seasonId = seasonId;

        return relationship;
    }

    public boolean isStarter() {
        return STARTER_VERSION.equals(version);
    }
}
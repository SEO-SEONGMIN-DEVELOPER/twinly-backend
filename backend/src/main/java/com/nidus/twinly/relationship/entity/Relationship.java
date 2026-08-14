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

    private Instant createdAt;

    public static Relationship create(Long userId, LocalDate date, String version, Long partnerUserId, Integer intimacy, String partnerModel, LocalDateTime updateTime) {
        Relationship relationship = new Relationship();

        relationship.userId = userId;
        relationship.date = date;
        relationship.version = version;
        relationship.partnerUserId = partnerUserId;
        relationship.intimacy = intimacy;
        relationship.partnerModel = partnerModel;
        relationship.updateTime = updateTime;
        relationship.createdAt = Instant.now();

        return relationship;
    }
}
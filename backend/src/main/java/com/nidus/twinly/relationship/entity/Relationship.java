package com.nidus.twinly.relationship.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

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

    private Long partnerUserId;

    private Integer intimacy;

    @Column(columnDefinition = "TEXT")
    private String partnerModel;

    private Instant createdAt;
}
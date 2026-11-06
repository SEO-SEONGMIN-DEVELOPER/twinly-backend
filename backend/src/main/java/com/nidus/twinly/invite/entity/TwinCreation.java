package com.nidus.twinly.invite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "twin_creations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinCreation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String diHash;

    private Instant createdAt;

    public static TwinCreation record(Long userId, String diHash, Instant createdAt) {
        TwinCreation recorded = new TwinCreation();
        recorded.userId = userId;
        recorded.diHash = diHash;
        recorded.createdAt = createdAt;
        return recorded;
    }
}

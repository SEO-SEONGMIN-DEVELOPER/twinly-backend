package com.nidus.twinly.legal.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long policyId;

    private Instant agreedAt;

    private Instant revokedAt;

    private Instant createdAt;

    public static Agreement create(Long userId, Long policyId, Instant agreedAt) {
        Agreement agreement = new Agreement();
        agreement.userId = userId;
        agreement.policyId = policyId;
        agreement.agreedAt = agreedAt;
        agreement.createdAt = agreedAt;
        return agreement;
    }
}

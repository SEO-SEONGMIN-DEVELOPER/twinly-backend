package com.nidus.twinly.anon.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "anon_session_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonSessionAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long anonSessionId;

    private Long policyId;

    private Instant agreedAt;

    private Instant revokedAt;

    private Instant createdAt;

    public static AnonSessionAgreement create(Long anonSessionId, Long policyId, Instant agreedAt) {
        AnonSessionAgreement agreement = new AnonSessionAgreement();
        agreement.anonSessionId = anonSessionId;
        agreement.policyId = policyId;
        agreement.agreedAt = agreedAt;
        agreement.createdAt = agreedAt;
        return agreement;
    }
}

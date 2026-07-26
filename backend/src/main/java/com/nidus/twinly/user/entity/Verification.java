package com.nidus.twinly.user.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.common.domain.VerificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private VerificationType type;

    private Instant verifiedAt;

    private Instant createdAt;

    public static Verification create(Long userId, VerificationType type, Instant verifiedAt) {
        Verification verification = new Verification();

        verification.userId = userId;
        verification.type = type;
        verification.verifiedAt = verifiedAt;
        verification.createdAt = Instant.now();

        return verification;
    }
}

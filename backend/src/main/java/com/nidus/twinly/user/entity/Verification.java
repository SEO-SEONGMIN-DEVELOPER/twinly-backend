package com.nidus.twinly.user.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.common.domain.VerificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
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

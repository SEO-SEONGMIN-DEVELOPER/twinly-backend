package com.nidus.twinly.auth.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.VerificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "verification_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private VerificationType type;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String contact;

    private UUID verificationToken;

    @Column(columnDefinition = "TEXT")
    private String code;

    private Instant codeExpiresAt;

    private UUID verifiedToken;

    private Instant verifiedTokenExpiresAt;

    private Instant verifiedAt;

    private Instant createdAt;

    public static VerificationSession create(VerificationType type, String contact, String code, Instant codeExpiresAt) {
        VerificationSession session = new VerificationSession();

        session.type = type;
        session.contact = contact;
        session.verificationToken = UUID.randomUUID();
        session.code = code;
        session.codeExpiresAt = codeExpiresAt;
        session.createdAt = Instant.now();

        return session;
    }

    public void verify(Instant verifiedTokenExpiresAt) {
        this.verifiedToken = UUID.randomUUID();
        this.verifiedTokenExpiresAt = verifiedTokenExpiresAt;
        this.verifiedAt = Instant.now();
    }
}
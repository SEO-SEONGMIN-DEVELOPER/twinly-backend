package com.nidus.twinly.auth.entity;

import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.VerificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private VerificationType type;

    @Convert(converter = EncryptedStringConverter.class)
    private String contact;

    private UUID verificationToken;

    private String code;

    private Instant codeExpiresAt;

    private UUID verifiedToken;

    private Instant verifiedTokenExpiresAt;

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
    }
}
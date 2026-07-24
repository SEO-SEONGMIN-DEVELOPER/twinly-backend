package com.nidus.twinly.auth.entity;

import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.VerificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "anon_session_verification_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonSessionVerificationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private VerificationType type;

    private Long anonSessionId;

    @Convert(converter = EncryptedStringConverter.class)
    private String contact;

    private UUID verificationToken;

    private String code;

    private Instant codeExpiresAt;

    private Instant verifiedAt;

    private Instant createdAt;

    public static AnonSessionVerificationSession create(VerificationType type, Long anonSessionId, String contact, String code, Instant codeExpiresAt) {
        AnonSessionVerificationSession session = new AnonSessionVerificationSession();

        session.type = type;
        session.anonSessionId = anonSessionId;
        session.contact = contact;
        session.verificationToken = UUID.randomUUID();
        session.code = code;
        session.codeExpiresAt = codeExpiresAt;
        session.createdAt = Instant.now();

        return session;
    }

    public void refresh(String contact, String code, Instant codeExpiresAt) {
        this.contact = contact;
        this.verificationToken = UUID.randomUUID();
        this.code = code;
        this.codeExpiresAt = codeExpiresAt;
        this.verifiedAt = null;
    }

    public void verify() {
        this.verifiedAt = Instant.now();
    }
}

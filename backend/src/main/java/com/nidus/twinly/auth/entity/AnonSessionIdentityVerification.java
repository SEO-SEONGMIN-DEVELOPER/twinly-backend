package com.nidus.twinly.auth.entity;

import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.Gender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Duration;
import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "anon_session_identity_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonSessionIdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long anonSessionId;

    private String identityVerificationId;

    private Instant expiresAt;

    private Instant verifiedAt;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String birthDate;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String phoneNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String ci;

    @Column(columnDefinition = "TEXT")
    private String ciHash;

    private Instant issueWindowStartedAt;

    private int issueCount;

    private Instant createdAt;

    public static AnonSessionIdentityVerification create(Long anonSessionId, String identityVerificationId, Instant expiresAt) {
        AnonSessionIdentityVerification verification = new AnonSessionIdentityVerification();

        verification.anonSessionId = anonSessionId;
        verification.identityVerificationId = identityVerificationId;
        verification.expiresAt = expiresAt;
        verification.issueWindowStartedAt = Instant.now();
        verification.issueCount = 1;
        verification.createdAt = Instant.now();

        return verification;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isRateLimited(Instant now, Duration issueWindow, int issueLimit) {
        return issueWindowStartedAt.plus(issueWindow).isAfter(now) && issueCount >= issueLimit;
    }

    public void countIssue(Instant now, Duration issueWindow) {
        if (issueWindowStartedAt.plus(issueWindow).isAfter(now)) {
            this.issueCount++;
            return;
        }

        this.issueWindowStartedAt = now;
        this.issueCount = 1;
    }

    public void refresh(String identityVerificationId, Instant expiresAt) {
        this.identityVerificationId = identityVerificationId;
        this.expiresAt = expiresAt;
    }

    public void verify(String name, String birthDate, Gender gender, String phoneNumber, String ci, String ciHash) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.ci = ci;
        this.ciHash = ciHash;
        this.verifiedAt = Instant.now();
    }
}

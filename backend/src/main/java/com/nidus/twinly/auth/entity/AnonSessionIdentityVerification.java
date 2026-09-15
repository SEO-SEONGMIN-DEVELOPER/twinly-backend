package com.nidus.twinly.auth.entity;

import com.nidus.twinly.common.crypto.EncryptedStringConverter;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.MobileCarrier;
import com.nidus.twinly.common.domain.NationalInfo;
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

    private String requestNo;

    private String transactionId;

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

    @Enumerated(EnumType.STRING)
    private NationalInfo nationalInfo;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private MobileCarrier mobileCarrier;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String di;

    @Column(columnDefinition = "TEXT")
    private String diHash;

    private Instant issueWindowStartedAt;

    private int issueCount;

    private Instant createdAt;

    public static AnonSessionIdentityVerification create(Long anonSessionId, String requestNo, String transactionId, Instant expiresAt) {
        AnonSessionIdentityVerification verification = new AnonSessionIdentityVerification();

        verification.anonSessionId = anonSessionId;
        verification.requestNo = requestNo;
        verification.transactionId = transactionId;
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

    public void refresh(String requestNo, String transactionId, Instant expiresAt) {
        this.requestNo = requestNo;
        this.transactionId = transactionId;
        this.expiresAt = expiresAt;
    }

    public void verify(String name, String birthDate, Gender gender, String phoneNumber, String di, String diHash,
                       NationalInfo nationalInfo, MobileCarrier mobileCarrier) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.di = di;
        this.diHash = diHash;
        this.nationalInfo = nationalInfo;
        this.mobileCarrier = mobileCarrier;
        this.verifiedAt = Instant.now();
    }
}

package com.nidus.twinly.auth.entity;

import com.nidus.twinly.auth.domain.IdentityVerificationResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "identity_verification_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityVerificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long anonSessionId;

    private String requestNo;

    private String transactionId;

    @Column(columnDefinition = "TEXT")
    private String diHash;

    @Enumerated(EnumType.STRING)
    private IdentityVerificationResult result;

    private Instant resultedAt;

    private Instant createdAt;

    public static IdentityVerificationLog issue(Long anonSessionId, String requestNo, String transactionId) {
        IdentityVerificationLog log = new IdentityVerificationLog();

        log.anonSessionId = anonSessionId;
        log.requestNo = requestNo;
        log.transactionId = transactionId;
        log.result = IdentityVerificationResult.ISSUED;
        log.createdAt = Instant.now();

        return log;
    }

    public void complete(IdentityVerificationResult result, String diHash) {
        this.result = result;
        this.diHash = diHash;
        this.resultedAt = Instant.now();
    }
}

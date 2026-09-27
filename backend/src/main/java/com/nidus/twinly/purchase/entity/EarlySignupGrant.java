package com.nidus.twinly.purchase.entity;

import com.nidus.twinly.common.time.KstTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "early_signup_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarlySignupGrant {

    public static final Period PERIOD = Period.ofMonths(2);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String diHash;

    private Instant expiresAt;

    private Instant grantedAt;

    private Instant createdAt;

    public static EarlySignupGrant assign(Long userId, String diHash, Instant assignedAt) {
        EarlySignupGrant assigned = new EarlySignupGrant();
        assigned.userId = userId;
        assigned.diHash = diHash;
        assigned.expiresAt = assignedAt.atZone(KstTimes.ZONE).plus(PERIOD).toInstant().truncatedTo(ChronoUnit.MILLIS);
        assigned.createdAt = assignedAt;
        return assigned;
    }
}

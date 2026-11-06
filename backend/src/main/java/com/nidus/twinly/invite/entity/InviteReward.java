package com.nidus.twinly.invite.entity;

import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.InviteRewardWaitingReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "invite_rewards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String diHash;

    @Enumerated(EnumType.STRING)
    private InviteRewardStatus status;

    @Enumerated(EnumType.STRING)
    private InviteRewardWaitingReason waitingReason;

    @Enumerated(EnumType.STRING)
    private InviteRewardMethod method;

    private String productId;

    private Instant baseExpiresAt;

    private Instant targetExpiresAt;

    private int attemptCount;

    private Instant attemptedAt;

    private Instant heldAt;

    private String appleTransactionId;

    private Instant appleOfferAcceptedAt;

    private Instant endsAt;

    private Instant grantedAt;

    private Instant createdAt;

    private Instant updatedAt;

    public static InviteReward create(Long userId, String diHash, Instant createdAt) {
        InviteReward created = new InviteReward();
        created.userId = userId;
        created.diHash = diHash;
        created.status = InviteRewardStatus.WAITING;
        created.createdAt = createdAt;
        created.updatedAt = createdAt;
        return created;
    }

    public boolean isGranted() {
        return status == InviteRewardStatus.GRANTED;
    }

    public boolean isHeld() {
        return heldAt != null;
    }

    public boolean isGranting() {
        return !isGranted() && targetExpiresAt != null;
    }

    public boolean isAttempted() {
        return attemptedAt != null;
    }
}

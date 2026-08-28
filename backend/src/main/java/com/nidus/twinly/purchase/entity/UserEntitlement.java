package com.nidus.twinly.purchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "user_entitlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 64)
    private String entitlement;

    private Instant expiresAt;

    private Instant syncedAt;

    private Instant createdAt;

    public static UserEntitlement create(Long userId, String entitlement, Instant expiresAt, Instant syncedAt) {
        UserEntitlement created = new UserEntitlement();
        created.userId = userId;
        created.entitlement = entitlement;
        created.expiresAt = expiresAt;
        created.syncedAt = syncedAt;
        created.createdAt = Instant.now();
        return created;
    }

    public void sync(Instant expiresAt, Instant syncedAt) {
        this.expiresAt = expiresAt;
        this.syncedAt = syncedAt;
    }

    public boolean isActiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}

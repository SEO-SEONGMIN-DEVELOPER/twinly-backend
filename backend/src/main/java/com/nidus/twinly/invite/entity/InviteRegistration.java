package com.nidus.twinly.invite.entity;

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
@Table(name = "invite_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long inviterUserId;

    private Long inviteeUserId;

    private Instant registeredAt;

    private Instant completedAt;

    public static InviteRegistration register(Long inviterUserId, Long inviteeUserId, Instant registeredAt) {
        InviteRegistration registered = new InviteRegistration();
        registered.inviterUserId = inviterUserId;
        registered.inviteeUserId = inviteeUserId;
        registered.registeredAt = registeredAt;
        return registered;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public void complete(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

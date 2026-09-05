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
@Table(name = "revenue_cat_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevenueCatEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String eventId;

    @Column(length = 64)
    private String type;

    private Long userId;

    @Column(length = 32)
    private String environment;

    private Instant receivedAt;

    private Instant completedAt;

    public static RevenueCatEvent receive(String eventId, String type, Long userId, String environment, Instant receivedAt) {
        RevenueCatEvent received = new RevenueCatEvent();
        received.eventId = eventId;
        received.type = type;
        received.userId = userId;
        received.environment = environment;
        received.receivedAt = receivedAt;
        return received;
    }

    public void complete(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

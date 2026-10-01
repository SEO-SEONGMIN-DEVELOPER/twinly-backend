package com.nidus.twinly.notification.entity;

import com.nidus.twinly.notification.domain.AppNotificationScheduleType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "app_notification_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppNotificationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long partnerUserId;

    @Enumerated(EnumType.STRING)
    private AppNotificationScheduleType type;

    private LocalDate simulationDate;

    private Instant scheduledAt;

    private Instant sentAt;

    private Instant createdAt;

    public static AppNotificationSchedule create(Long userId, Long partnerUserId, AppNotificationScheduleType type,
                                                 LocalDate simulationDate, Instant scheduledAt) {
        AppNotificationSchedule schedule = new AppNotificationSchedule();

        schedule.userId = userId;
        schedule.partnerUserId = partnerUserId;
        schedule.type = type;
        schedule.simulationDate = simulationDate;
        schedule.scheduledAt = scheduledAt;
        schedule.createdAt = Instant.now();

        return schedule;
    }

    public void markSent(Instant sentAt) {
        this.sentAt = sentAt;
    }
}

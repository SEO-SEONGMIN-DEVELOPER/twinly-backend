package com.nidus.twinly.notification.entity;

import com.nidus.twinly.notification.domain.NotificationChannel;
import com.nidus.twinly.notification.domain.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private NotificationType type;

    private Boolean enabled;

    private Instant createdAt;

    public static NotificationSetting create(Long userId, NotificationChannel channel, NotificationType type, Boolean enabled) {
        NotificationSetting notificationSetting = new NotificationSetting();
        notificationSetting.userId = userId;
        notificationSetting.channel = channel;
        notificationSetting.type = type;
        notificationSetting.enabled = enabled;
        notificationSetting.createdAt = Instant.now();
        return notificationSetting;
    }

    public void changeEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

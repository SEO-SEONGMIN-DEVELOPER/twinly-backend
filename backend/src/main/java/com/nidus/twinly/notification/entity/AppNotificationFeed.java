package com.nidus.twinly.notification.entity;

import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "app_notification_feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppNotificationFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    private AppNotificationFeedType type;

    @Enumerated(EnumType.STRING)
    private AppNotificationFeedTargetType targetKind;

    private Long targetUserId;

    private Long targetChatRoomId;

    private Instant readAt;

    private Instant createdAt;

    public void markRead() {
        this.readAt = Instant.now();
    }
}

package com.nidus.twinly.notification.entity;

import com.nidus.twinly.notification.domain.AppNotificationFeedTargetType;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalDate;

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

    private LocalDate simulationDate;

    private Instant readAt;

    private Instant createdAt;

    public static AppNotificationFeed createProfileTarget(Long userId, AppNotificationFeedType type, String title,
                                                          String body, Long targetUserId, LocalDate simulationDate) {
        AppNotificationFeed feed = create(userId, type, title, body);

        feed.targetKind = AppNotificationFeedTargetType.PROFILE;
        feed.targetUserId = targetUserId;
        feed.simulationDate = simulationDate;

        return feed;
    }

    public static AppNotificationFeed createChatTarget(Long userId, AppNotificationFeedType type, String title,
                                                       String body, Long targetChatRoomId) {
        AppNotificationFeed feed = create(userId, type, title, body);

        feed.targetKind = AppNotificationFeedTargetType.CHAT;
        feed.targetChatRoomId = targetChatRoomId;

        return feed;
    }

    private static AppNotificationFeed create(Long userId, AppNotificationFeedType type, String title, String body) {
        AppNotificationFeed feed = new AppNotificationFeed();

        feed.userId = userId;
        feed.type = type;
        feed.title = title;
        feed.body = body;
        feed.createdAt = Instant.now();

        return feed;
    }

    public void markRead() {
        this.readAt = Instant.now();
    }
}

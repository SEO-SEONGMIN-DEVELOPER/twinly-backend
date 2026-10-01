package com.nidus.twinly.user.entity;

import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.common.feedback.FeedbackType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "user_feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private FeedbackType type;

    private String detail;

    @Enumerated(EnumType.STRING)
    private AppPlatform appPlatform;

    private String appVersion;

    private Instant createdAt;

    public static UserFeedback create(Long userId, FeedbackType type, String detail, AppPlatform appPlatform, String appVersion) {
        UserFeedback userFeedback = new UserFeedback();
        userFeedback.userId = userId;
        userFeedback.type = type;
        userFeedback.detail = detail;
        userFeedback.appPlatform = appPlatform;
        userFeedback.appVersion = appVersion;
        userFeedback.createdAt = Instant.now();
        return userFeedback;
    }
}

package com.nidus.twinly.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "user_feedback_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFeedbackOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long feedbackId;

    private Long optionId;

    private Instant createdAt;

    public static UserFeedbackOption create(Long feedbackId, Long optionId) {
        UserFeedbackOption userFeedbackOption = new UserFeedbackOption();
        userFeedbackOption.feedbackId = feedbackId;
        userFeedbackOption.optionId = optionId;
        userFeedbackOption.createdAt = Instant.now();
        return userFeedbackOption;
    }
}

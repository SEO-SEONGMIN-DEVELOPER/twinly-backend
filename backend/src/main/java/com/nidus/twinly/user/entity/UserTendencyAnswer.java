package com.nidus.twinly.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "user_tendency_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTendencyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long questionId;

    private Long optionId;

    private Instant createdAt;

    public static UserTendencyAnswer create(Long userId, Long questionId, Long optionId) {
        UserTendencyAnswer userTendencyAnswer = new UserTendencyAnswer();
        userTendencyAnswer.userId = userId;
        userTendencyAnswer.questionId = questionId;
        userTendencyAnswer.optionId = optionId;
        userTendencyAnswer.createdAt = Instant.now();
        return userTendencyAnswer;
    }
}

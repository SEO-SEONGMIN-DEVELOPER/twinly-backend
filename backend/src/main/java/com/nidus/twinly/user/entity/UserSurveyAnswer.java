package com.nidus.twinly.user.entity;

import com.nidus.twinly.common.survey.SurveyOptionName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "user_survey_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Integer questionId;

    @Enumerated(EnumType.STRING)
    private SurveyOptionName optionName;

    private Instant createdAt;

    public static UserSurveyAnswer create(Long userId, Integer questionId, SurveyOptionName optionName) {
        UserSurveyAnswer userSurveyAnswer = new UserSurveyAnswer();
        userSurveyAnswer.userId = userId;
        userSurveyAnswer.questionId = questionId;
        userSurveyAnswer.optionName = optionName;
        userSurveyAnswer.createdAt = Instant.now();
        return userSurveyAnswer;
    }
}

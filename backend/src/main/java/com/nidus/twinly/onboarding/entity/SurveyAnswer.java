package com.nidus.twinly.onboarding.entity;

import com.nidus.twinly.common.survey.SurveyOptionName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "survey_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long anonSessionId;

    private Integer qId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private SurveyOptionName optionName;

    private Instant createdAt;

    public static SurveyAnswer create(Long anonSessionId, Integer qId, SurveyOptionName optionName) {
        SurveyAnswer surveyAnswer = new SurveyAnswer();
        surveyAnswer.anonSessionId = anonSessionId;
        surveyAnswer.qId = qId;
        surveyAnswer.optionName = optionName;
        surveyAnswer.createdAt = Instant.now();
        return surveyAnswer;
    }
}
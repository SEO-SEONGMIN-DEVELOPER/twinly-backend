package com.nidus.twinly.twinupdate.entity;

import com.nidus.twinly.common.survey.SurveyOptionName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "twin_update_survey_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinUpdateSurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long twinUpdateId;

    private Integer questionId;

    @Enumerated(EnumType.STRING)
    private SurveyOptionName optionName;

    private Instant createdAt;
}

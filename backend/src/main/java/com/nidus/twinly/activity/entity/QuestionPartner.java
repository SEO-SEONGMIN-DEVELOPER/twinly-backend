package com.nidus.twinly.activity.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "question_partners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long questionId;

    private Long userId;

    private Instant createdAt;

    public static QuestionPartner create(Long questionId, Long userId) {
        QuestionPartner questionPartner = new QuestionPartner();

        questionPartner.questionId = questionId;
        questionPartner.userId = userId;
        questionPartner.createdAt = Instant.now();

        return questionPartner;
    }
}
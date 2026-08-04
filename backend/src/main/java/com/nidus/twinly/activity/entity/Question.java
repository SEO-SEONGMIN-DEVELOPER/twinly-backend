package com.nidus.twinly.activity.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.activity.domain.QuestionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@DynamicUpdate
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private LocalDate date;

    @Column(columnDefinition = "TEXT")
    private String version;

    @Column(nullable = false)
    private LocalTime time;

    @Enumerated(EnumType.STRING)
    private QuestionType type;

    @Column(columnDefinition = "TEXT")
    private String text;

    private List<String> options;

    @Column(columnDefinition = "TEXT")
    private String choice;

    private Instant answeredAt;

    private Boolean isSkipped;

    private Instant createdAt;

    public static Question create(Long userId, LocalDate date, String version, LocalTime time, QuestionType type,
                                  String text, List<String> options) {
        Question question = new Question();

        question.userId = userId;
        question.date = date;
        question.version = version;
        question.time = time;
        question.type = type;
        question.text = text;
        question.options = options;
        question.isSkipped = false;
        question.createdAt = Instant.now();

        return question;
    }

    public void answer(String choice) {
        this.choice = choice;
        this.answeredAt = Instant.now();
    }

    public void skip() {
        this.isSkipped = true;
    }
}
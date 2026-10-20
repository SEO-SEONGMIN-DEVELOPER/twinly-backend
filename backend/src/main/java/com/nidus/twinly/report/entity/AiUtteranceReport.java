package com.nidus.twinly.report.entity;

import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.domain.ViolationKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "ai_utterance_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUtteranceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long reportedUserId;

    private Long sceneId;

    @Column(columnDefinition = "TEXT")
    private String utteranceText;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    private ViolationKind violationKind;

    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    @Column(columnDefinition = "TEXT")
    private String actionNote;

    private Instant processedAt;

    private Instant createdAt;

    public static AiUtteranceReport create(Long userId, Long reportedUserId, Long sceneId, String utteranceText, String reason) {
        AiUtteranceReport report = new AiUtteranceReport();

        report.userId = userId;
        report.reportedUserId = reportedUserId;
        report.sceneId = sceneId;
        report.utteranceText = utteranceText;
        report.reason = reason;
        report.status = ReportStatus.PENDING;
        report.createdAt = Instant.now();

        return report;
    }

    public void review(ReportStatus status, ViolationKind violationKind, String reviewNote, String actionNote, Instant now) {
        this.status = status;
        this.violationKind = violationKind;
        this.reviewNote = reviewNote;
        this.actionNote = actionNote;
        this.processedAt = status.isProcessed() ? now : null;
    }
}

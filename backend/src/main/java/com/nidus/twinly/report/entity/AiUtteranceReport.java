package com.nidus.twinly.report.entity;

import com.nidus.twinly.report.domain.ReportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    private String utteranceText;

    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ReportStatus status;

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
}

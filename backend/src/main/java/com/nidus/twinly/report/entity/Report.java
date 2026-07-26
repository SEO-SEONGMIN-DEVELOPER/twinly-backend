package com.nidus.twinly.report.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.domain.ReportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long reportedUserId;

    @Enumerated(EnumType.STRING)
    private ReportReason reason;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    private Instant createdAt;

    public static Report create(Long userId, Long reportedUserId, ReportReason reason, String detail) {
        Report report = new Report();

        report.userId = userId;
        report.reportedUserId = reportedUserId;
        report.reason = reason;
        report.detail = detail;
        report.status = ReportStatus.PENDING;
        report.createdAt = Instant.now();

        return report;
    }
}
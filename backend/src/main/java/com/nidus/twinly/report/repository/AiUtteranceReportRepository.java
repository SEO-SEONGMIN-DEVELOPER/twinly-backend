package com.nidus.twinly.report.repository;

import com.nidus.twinly.report.entity.AiUtteranceReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUtteranceReportRepository extends JpaRepository<AiUtteranceReport, Long> {
}

package com.nidus.twinly.report.repository;

import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByReportedUserIdAndStatusNot(Long reportedUserId, ReportStatus status);
}

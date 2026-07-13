package com.nidus.twinly.report.repository;

import com.nidus.twinly.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}
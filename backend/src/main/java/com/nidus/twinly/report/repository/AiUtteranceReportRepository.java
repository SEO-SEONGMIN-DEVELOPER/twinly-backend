package com.nidus.twinly.report.repository;

import com.nidus.twinly.report.entity.AiUtteranceReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiUtteranceReportRepository extends JpaRepository<AiUtteranceReport, Long> {

    List<AiUtteranceReport> findAllByUserIdAndSceneId(Long userId, Long sceneId);
}

package com.nidus.twinly.report.service;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.report.dto.command.ReportAiUtteranceCommand;
import com.nidus.twinly.report.dto.command.ReportCommand;
import com.nidus.twinly.report.dto.result.ReportResult;
import com.nidus.twinly.report.entity.AiUtteranceReport;
import com.nidus.twinly.report.entity.Report;
import com.nidus.twinly.report.repository.AiUtteranceReportRepository;
import com.nidus.twinly.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Boolean AUTO_BLOCK = true;

    private final ReportRepository reportRepository;
    private final BlockRepository blockRepository;
    private final AiUtteranceReportRepository aiUtteranceReportRepository;
    private final SceneRepository sceneRepository;

    @Transactional
    public ReportResult report(Long userId, Long reportedUserId, ReportCommand command) {
        if (userId.equals(reportedUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신을 신고할 수 없습니다.");
        }

        reportRepository.save(Report.create(userId, reportedUserId, command.reason(), command.detail()));

        if (AUTO_BLOCK && !blockRepository.existsByUserIdAndBlockedUserId(userId, reportedUserId)) {
            blockRepository.save(Block.create(userId, reportedUserId));
        }

        return new ReportResult(AUTO_BLOCK);
    }

    @Transactional
    public void aiUtterance(Long userId, ReportAiUtteranceCommand command) {
        Long reportedUserId = command.targetUserId();
        Long sceneId = command.sceneId();

        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 씬입니다."));
        if (!scene.getUserId().equals(reportedUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대상과 씬이 일치하지 않습니다.");
        }

        aiUtteranceReportRepository.save(
                AiUtteranceReport.create(userId, reportedUserId, sceneId, command.utteranceText(), command.reason()));
    }
}

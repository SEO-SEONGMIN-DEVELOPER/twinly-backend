package com.nidus.twinly.report.service;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.report.dto.command.ReportAiUtteranceCommand;
import com.nidus.twinly.report.dto.command.ReportUserCommand;
import com.nidus.twinly.report.dto.result.ReportUserResult;
import com.nidus.twinly.report.entity.AiUtteranceReport;
import com.nidus.twinly.report.entity.Report;
import com.nidus.twinly.report.repository.AiUtteranceReportRepository;
import com.nidus.twinly.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Boolean AUTO_BLOCK = true;

    private final ReportRepository reportRepository;
    private final BlockRepository blockRepository;
    private final AiUtteranceReportRepository aiUtteranceReportRepository;
    private final SceneRepository sceneRepository;

    @Transactional
    public ReportUserResult reportUser(Long userId, ReportUserCommand command) {
        Long reportedUserId = command.targetUserId();

        if (userId.equals(reportedUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
        }

        reportRepository.save(Report.create(userId, reportedUserId, command.reason(), command.detail()));

        if (AUTO_BLOCK && !blockRepository.existsByUserIdAndBlockedUserId(userId, reportedUserId)) {
            blockRepository.save(Block.create(userId, reportedUserId));
        }

        return new ReportUserResult(AUTO_BLOCK);
    }

    @Transactional
    public void reportAiUtterance(Long userId, ReportAiUtteranceCommand command) {
        Long reportedUserId = command.targetUserId();
        Long sceneId = command.sceneId();

        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        if (!scene.getUserId().equals(reportedUserId)) {
            throw new BusinessException(ErrorCode.SCENE_TARGET_MISMATCH);
        }

        aiUtteranceReportRepository.save(
                AiUtteranceReport.create(userId, reportedUserId, sceneId, command.utteranceText(), command.reason()));
    }
}

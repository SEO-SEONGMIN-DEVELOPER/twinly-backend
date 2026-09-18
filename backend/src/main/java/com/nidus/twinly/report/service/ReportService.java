package com.nidus.twinly.report.service;

import com.nidus.twinly.activity.entity.Scene;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
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
import com.nidus.twinly.report.event.AiUtteranceReportedEvent;
import com.nidus.twinly.report.event.UserReportedEvent;
import com.nidus.twinly.report.repository.AiUtteranceReportRepository;
import com.nidus.twinly.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final BlockRepository blockRepository;
    private final AiUtteranceReportRepository aiUtteranceReportRepository;
    private final SceneRepository sceneRepository;
    private final ScenePartnerRepository scenePartnerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReportUserResult reportUser(Long userId, ReportUserCommand command) {
        Long reportedUserId = command.targetUserId();

        if (userId.equals(reportedUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
        }

        boolean alreadyReported = reportRepository.findAllByUserIdAndReportedUserId(userId, reportedUserId).stream()
                .anyMatch(report -> report.getReason() == command.reason()
                        && Objects.equals(report.getDetail(), command.detail()));

        if (!alreadyReported) {
            Report report = reportRepository.save(Report.create(userId, reportedUserId, command.reason(), command.detail()));
            eventPublisher.publishEvent(new UserReportedEvent(
                    report.getId(), userId, reportedUserId, report.getReason(), report.getDetail()));
        }

        if (!blockRepository.existsByUserIdAndBlockedUserId(userId, reportedUserId)) {
            blockRepository.save(Block.create(userId, reportedUserId));
        }

        return new ReportUserResult(true);
    }

    @Transactional
    public void reportAiUtterance(Long userId, ReportAiUtteranceCommand command) {
        Long reportedUserId = command.targetUserId();
        Long sceneId = command.sceneId();

        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        if (!scene.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_SCENE_OWNER);
        }

        if (!scenePartnerRepository.existsBySceneIdAndUserId(sceneId, reportedUserId)) {
            throw new BusinessException(ErrorCode.SCENE_TARGET_MISMATCH);
        }

        boolean alreadyReported = aiUtteranceReportRepository.findAllByUserIdAndSceneId(userId, sceneId).stream()
                .anyMatch(report -> Objects.equals(report.getUtteranceText(), command.utteranceText())
                        && Objects.equals(report.getReason(), command.reason()));

        if (alreadyReported) {
            return;
        }

        AiUtteranceReport report = aiUtteranceReportRepository.save(
                AiUtteranceReport.create(userId, reportedUserId, sceneId, command.utteranceText(), command.reason()));
        eventPublisher.publishEvent(new AiUtteranceReportedEvent(
                report.getId(), userId, reportedUserId, sceneId, report.getUtteranceText(), report.getReason()));
    }
}

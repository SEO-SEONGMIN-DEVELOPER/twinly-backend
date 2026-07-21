package com.nidus.twinly.report.service;

import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.report.dto.command.ReportCommand;
import com.nidus.twinly.report.dto.result.ReportResult;
import com.nidus.twinly.report.entity.Report;
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
}

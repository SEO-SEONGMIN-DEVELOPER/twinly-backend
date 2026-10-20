package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.domain.CaseStatusFilter;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.command.AdminAiUtteranceReportReviewCommand;
import com.nidus.twinly.admin.dto.command.AdminSuggestionReviewCommand;
import com.nidus.twinly.admin.dto.command.AdminUserReportReviewCommand;
import com.nidus.twinly.admin.dto.result.AdminAiUtteranceReportResult;
import com.nidus.twinly.admin.dto.result.AdminCaseSummaryResult;
import com.nidus.twinly.admin.dto.result.AdminFeedbackResult;
import com.nidus.twinly.admin.dto.result.AdminGenderCountResult;
import com.nidus.twinly.admin.dto.result.AdminMemberSummaryResult;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.admin.dto.result.AdminUserReportResult;
import com.nidus.twinly.admin.dto.result.AdminWithdrawalReasonListResult;
import com.nidus.twinly.admin.reader.AdminCaseReader;
import com.nidus.twinly.admin.reader.AdminCaseReader.AiReportRow;
import com.nidus.twinly.admin.reader.AdminCaseReader.FeedbackRow;
import com.nidus.twinly.admin.reader.AdminCaseReader.PendingCounts;
import com.nidus.twinly.admin.reader.AdminCaseReader.UserReportRow;
import com.nidus.twinly.admin.reader.AdminMemberReader;
import com.nidus.twinly.common.feedback.FeedbackOption;
import com.nidus.twinly.common.feedback.FeedbackOptionLoader;
import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.report.repository.AiUtteranceReportRepository;
import com.nidus.twinly.report.repository.ReportRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.entity.UserFeedback;
import com.nidus.twinly.user.repository.UserFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminCaseService {

    private final AdminCaseReader adminCaseReader;
    private final AdminMemberReader adminMemberReader;
    private final ReportRepository reportRepository;
    private final AiUtteranceReportRepository aiUtteranceReportRepository;
    private final UserFeedbackRepository userFeedbackRepository;
    private final FeedbackOptionLoader feedbackOptionLoader;

    @Transactional(readOnly = true)
    public AdminCaseSummaryResult summary() {
        PendingCounts counts = adminCaseReader.pendingCounts();

        return new AdminCaseSummaryResult(
                counts.userReports(),
                counts.aiReports(),
                AdminGenderCountResult.from(counts.reportersByGender()),
                counts.suggestions());
    }

    @Transactional(readOnly = true)
    public AdminPageResult<AdminUserReportResult> userReports(CaseStatusFilter filter, int page, int size) {
        AdminPageResult<UserReportRow> rows = adminCaseReader.userReports(filter, page, size);
        Map<Long, User> userById = adminMemberReader.usersById(rows.items().stream()
                .flatMap(row -> Stream.of(row.reporterId(), row.reportedId()))
                .collect(Collectors.toSet()));

        return rows.map(row -> new AdminUserReportResult(
                row.id(),
                summary(row.reporterId(), userById),
                summary(row.reportedId(), userById),
                row.reason(),
                row.detail(),
                row.status(),
                row.reviewNote(),
                row.actionNote(),
                row.processedAt(),
                row.createdAt()));
    }

    @Transactional(readOnly = true)
    public AdminPageResult<AdminAiUtteranceReportResult> aiUtteranceReports(CaseStatusFilter filter, int page, int size) {
        AdminPageResult<AiReportRow> rows = adminCaseReader.aiReports(filter, page, size);
        Map<Long, User> userById = adminMemberReader.usersById(rows.items().stream()
                .flatMap(row -> Stream.of(row.reporterId(), row.reportedId()))
                .collect(Collectors.toSet()));

        return rows.map(row -> new AdminAiUtteranceReportResult(
                row.id(),
                summary(row.reporterId(), userById),
                summary(row.reportedId(), userById),
                row.sceneId(),
                row.scenePlace(),
                row.sceneStartsAt() == null ? null : KstTimes.toKstOffsetDateTime(row.sceneStartsAt()),
                row.utteranceText(),
                row.reason(),
                row.status(),
                row.violationKind(),
                row.reviewNote(),
                row.actionNote(),
                row.processedAt(),
                row.createdAt()));
    }

    @Transactional(readOnly = true)
    public AdminPageResult<AdminFeedbackResult> suggestions(CaseStatusFilter filter, int page, int size) {
        return feedbacks(FeedbackType.SUGGESTION, filter, page, size);
    }

    @Transactional(readOnly = true)
    public AdminWithdrawalReasonListResult withdrawalReasons(int page, int size) {
        Map<Long, Long> countByOptionId = adminCaseReader.withdrawalOptionCounts();
        List<AdminWithdrawalReasonListResult.ReasonCount> reasonCounts = feedbackOptionLoader.getOptions(FeedbackType.WITHDRAWAL).stream()
                .map(option -> new AdminWithdrawalReasonListResult.ReasonCount(option.label(), countByOptionId.getOrDefault(option.id(), 0L)))
                .toList();

        return new AdminWithdrawalReasonListResult(
                adminCaseReader.withdrawalCount(),
                reasonCounts,
                feedbacks(FeedbackType.WITHDRAWAL, null, page, size));
    }

    @Transactional
    public void reviewUserReport(AdminUserReportReviewCommand command) {
        reportRepository.findById(command.reportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND))
                .review(command.status(), command.reviewNote(), command.actionNote(), Instant.now());
    }

    @Transactional
    public void reviewAiUtteranceReport(AdminAiUtteranceReportReviewCommand command) {
        aiUtteranceReportRepository.findById(command.reportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND))
                .review(command.status(), command.violationKind(), command.reviewNote(), command.actionNote(), Instant.now());
    }

    @Transactional
    public void reviewSuggestion(AdminSuggestionReviewCommand command) {
        userFeedbackRepository.findById(command.feedbackId())
                .filter(feedback -> feedback.getType() == FeedbackType.SUGGESTION)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND))
                .review(command.status(), command.memo());
    }

    private AdminPageResult<AdminFeedbackResult> feedbacks(FeedbackType type, CaseStatusFilter filter, int page, int size) {
        AdminPageResult<FeedbackRow> rows = adminCaseReader.feedbacks(type, filter, page, size);
        Map<Long, String> labelByOptionId = feedbackOptionLoader.getOptions(type).stream()
                .collect(Collectors.toMap(FeedbackOption::id, FeedbackOption::label));
        Map<Long, List<Long>> optionIdsByFeedbackId = adminCaseReader.optionIdsByFeedbackId(rows.items().stream().map(FeedbackRow::id).toList());
        Map<Long, User> userById = adminMemberReader.usersById(rows.items().stream().map(FeedbackRow::authorId).collect(Collectors.toSet()));

        return rows.map(row -> new AdminFeedbackResult(
                row.id(),
                summary(row.authorId(), userById),
                optionIdsByFeedbackId.getOrDefault(row.id(), List.of()).stream()
                        .map(optionId -> labelByOptionId.getOrDefault(optionId, "알 수 없는 선택지"))
                        .toList(),
                row.detail(),
                row.appPlatform(),
                row.appVersion(),
                row.status(),
                row.memo(),
                row.createdAt()));
    }

    private AdminMemberSummaryResult summary(Long userId, Map<Long, User> userById) {
        User user = userById.get(userId);

        if (user == null) {
            return new AdminMemberSummaryResult(userId, null, null, null, null);
        }

        return new AdminMemberSummaryResult(user.getId(), user.getNickname(), user.getGender(), user.getOrganization(), MemberStatus.of(user));
    }
}

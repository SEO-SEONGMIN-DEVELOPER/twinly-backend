package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.dto.command.AdminSchoolVerificationDecideStudentIdCommand;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdDetailHistoryItemResult;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdDetailResult;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdsItemResult;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.purchase.writer.EarlySignupGrantWriter;
import com.nidus.twinly.schoolverification.domain.StudentIdDecision;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import com.nidus.twinly.schoolverification.entity.StudentIdSubmission;
import com.nidus.twinly.schoolverification.event.SchoolVerificationApprovedEvent;
import com.nidus.twinly.schoolverification.repository.StudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.storage.StudentIdPhotoStorage;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSchoolVerificationService {

    private final StudentIdPhotoStorage studentIdPhotoStorage;
    private final EarlySignupGrantWriter earlySignupGrantWriter;
    private final AppNotificationFeedWriter appNotificationFeedWriter;
    private final ApplicationEventPublisher eventPublisher;

    private final StudentIdSubmissionRepository studentIdSubmissionRepository;
    private final UserRepository userRepository;

    public AdminPageResult<AdminSchoolVerificationStudentIdsItemResult> studentIds(StudentIdSubmissionStatus status, Integer page, Integer size) {
        Instant now = Instant.now();
        Page<StudentIdSubmission> submissions = studentIdSubmissionRepository.findForAdmin(status, PageRequest.of(page, size));
        Map<Long, User> usersById = userRepository.findAllById(submissions.getContent().stream().map(StudentIdSubmission::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminSchoolVerificationStudentIdsItemResult> items = submissions.getContent().stream()
                .map(submission -> new AdminSchoolVerificationStudentIdsItemResult(
                        submission.getSubmissionId(),
                        submission.getUserId(),
                        usersById.get(submission.getUserId()).displayNickname(),
                        submission.getOrganization(),
                        submission.getStatus(),
                        submission.getReviewer(),
                        submission.getSubmittedAt(),
                        submission.getDecidedAt(),
                        elapsedMinutes(submission, now)))
                .toList();

        return new AdminPageResult<>(items, page, size, submissions.getTotalElements());
    }

    private Long elapsedMinutes(StudentIdSubmission submission, Instant now) {
        Instant waitedUntil = submission.isPending() ? now : submission.getDecidedAt();
        return waitedUntil == null ? null : Duration.between(submission.getSubmittedAt(), waitedUntil).toMinutes();
    }

    public AdminSchoolVerificationStudentIdDetailResult studentIdDetail(UUID submissionId) {
        StudentIdSubmission submission = studentIdSubmissionRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_ID_SUBMISSION_NOT_FOUND));

        User user = userRepository.findById(submission.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String photoUrl = submission.isPending() && !submission.isPhotoDeleted()
                ? studentIdPhotoStorage.reviewUrl(submission.getReviewKey())
                : null;

        List<AdminSchoolVerificationStudentIdDetailHistoryItemResult> history = studentIdSubmissionRepository.findAllByUserIdOrderByIdDesc(user.getId()).stream()
                .filter(other -> !other.getId().equals(submission.getId()))
                .map(other -> new AdminSchoolVerificationStudentIdDetailHistoryItemResult(
                        other.getSubmissionId(),
                        other.getStatus(),
                        other.getRejectReason(),
                        other.getReviewer(),
                        other.getSubmittedAt(),
                        other.getDecidedAt()))
                .toList();

        return new AdminSchoolVerificationStudentIdDetailResult(
                submission.getSubmissionId(),
                user.getId(),
                user.displayNickname(),
                submission.getStatus(),
                submission.getRejectReason(),
                submission.getReviewer(),
                photoUrl,
                user.displayFullName(),
                submission.getOrganization(),
                submission.getSubmittedAt(),
                submission.getDecidedAt(),
                history
        );
    }

    @Transactional
    public void decideStudentId(UUID submissionId, AdminSchoolVerificationDecideStudentIdCommand command) {
        if (command.decision() == StudentIdDecision.REJECTED && command.rejectReason() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "반려할 때는 반려 사유가 필요합니다.");
        }

        StudentIdSubmission submission = studentIdSubmissionRepository.findWithLockBySubmissionId(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_ID_SUBMISSION_NOT_FOUND));

        if (!submission.isPending()) {
            throw new BusinessException(ErrorCode.STUDENT_ID_REVIEW_CONFLICT, "이미 처리된 제출입니다: " + submissionId);
        }

        User user = userRepository.findWithLockById(submission.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.STUDENT_ID_REVIEW_CONFLICT, "탈퇴 신청한 회원의 제출입니다: " + submissionId);
        }

        if (command.decision() == StudentIdDecision.APPROVED) {
            submission.approve(command.reviewer());
            user.approveSchoolVerification();
            earlySignupGrantWriter.assign(user.getId(), user.getGender(), user.getDiHash(), Instant.now());
            appNotificationFeedWriter.writeSchoolVerificationApproved(user.getId());
            eventPublisher.publishEvent(new SchoolVerificationApprovedEvent(user.getId()));
            return;
        }

        submission.reject(command.rejectReason(), command.reviewer());
        user.rejectSchoolVerification();
        appNotificationFeedWriter.writeSchoolVerificationRejected(user.getId(), command.rejectReason());
    }
}

package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.dto.command.AdminSchoolVerificationDecideStudentIdCommand;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdDetailResult;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdsItemResult;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.purchase.writer.EarlySignupGrantWriter;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;
import com.nidus.twinly.schoolverification.domain.StudentIdDecision;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import com.nidus.twinly.schoolverification.entity.StudentIdSubmission;
import com.nidus.twinly.schoolverification.event.SchoolVerificationApprovedEvent;
import com.nidus.twinly.schoolverification.repository.StudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.storage.StudentIdPhotoStorage;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminSchoolVerificationServiceUnitTest {

    private static final Long USER_ID = 31L;
    private static final String REVIEWER = "ops@trytwinly.com";

    @Mock
    StudentIdPhotoStorage studentIdPhotoStorage;

    @Mock
    EarlySignupGrantWriter earlySignupGrantWriter;

    @Mock
    AppNotificationFeedWriter appNotificationFeedWriter;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    StudentIdSubmissionRepository studentIdSubmissionRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    AdminSchoolVerificationService adminSchoolVerificationService;

    @Test
    @DisplayName("대기 목록: 제출마다 회원 닉네임·고른 학교·기다린 분을 담고 페이지 정보를 그대로 돌려준다")
    void studentIds_maps_items() {
        // given: 90분 전에 낸 대기 제출 1건
        StudentIdSubmission submission = pending();
        ReflectionTestUtils.setField(submission, "submittedAt", Instant.now().minus(Duration.ofMinutes(90)));
        given(studentIdSubmissionRepository.findReviewQueue(PageRequest.of(0, 20)))
                .willReturn(new PageImpl<>(List.of(submission), PageRequest.of(0, 20), 1));
        given(userRepository.findAllById(List.of(USER_ID))).willReturn(List.of(user()));

        // when: 대기 목록 조회
        AdminPageResult<AdminSchoolVerificationStudentIdsItemResult> result = adminSchoolVerificationService.studentIds(0, 20);

        // then: 항목 매핑 + 전체 수
        assertThat(result.totalCount()).isEqualTo(1);
        AdminSchoolVerificationStudentIdsItemResult item = result.items().getFirst();
        assertThat(item.nickname()).isEqualTo("가온");
        assertThat(item.organization()).isEqualTo("트윈리대학교");
        assertThat(item.elapsedMinutes()).isBetween(90L, 91L);
    }

    @Test
    @DisplayName("상세: 대기 중이면 5분짜리 사진 주소를 주고, 이전 제출은 이력으로 따로 담는다")
    void studentIdDetail_pending_has_photo_url_and_history() {
        // given: 반려된 이전 제출과 지금 대기 중인 제출
        StudentIdSubmission previous = pending();
        previous.reject(StudentIdRejectReason.BLURRY, REVIEWER);
        ReflectionTestUtils.setField(previous, "id", 1L);
        StudentIdSubmission current = pending();
        ReflectionTestUtils.setField(current, "id", 2L);
        given(studentIdSubmissionRepository.findBySubmissionId(current.getSubmissionId())).willReturn(Optional.of(current));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(studentIdPhotoStorage.reviewUrl(current.getReviewKey())).willReturn("https://s3/review");
        given(studentIdSubmissionRepository.findAllByUserIdOrderByIdDesc(USER_ID)).willReturn(List.of(current, previous));

        // when: 상세 조회
        AdminSchoolVerificationStudentIdDetailResult result = adminSchoolVerificationService.studentIdDetail(current.getSubmissionId());

        // then: 사진 주소·본인인증 성명 + 이력에는 이전 제출만
        assertThat(result.photoUrl()).isEqualTo("https://s3/review");
        assertThat(result.identityName()).isEqualTo("홍길동");
        assertThat(result.history()).hasSize(1);
        assertThat(result.history().getFirst().rejectReason()).isEqualTo(StudentIdRejectReason.BLURRY);
    }

    @Test
    @DisplayName("상세: 판정한 제출은 사진 주소를 만들지 않는다")
    void studentIdDetail_decided_has_no_photo_url() {
        // given: 이미 승인한 제출
        StudentIdSubmission submission = pending();
        submission.approve(REVIEWER);
        ReflectionTestUtils.setField(submission, "id", 1L);
        given(studentIdSubmissionRepository.findBySubmissionId(submission.getSubmissionId())).willReturn(Optional.of(submission));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(studentIdSubmissionRepository.findAllByUserIdOrderByIdDesc(USER_ID)).willReturn(List.of(submission));

        // when: 상세 조회
        AdminSchoolVerificationStudentIdDetailResult result = adminSchoolVerificationService.studentIdDetail(submission.getSubmissionId());

        // then: 사진 주소 없음, 판정 정보는 그대로
        assertThat(result.photoUrl()).isNull();
        assertThat(result.reviewer()).isEqualTo(REVIEWER);
        then(studentIdPhotoStorage).should(never()).reviewUrl(anyString());
    }

    @Test
    @DisplayName("승인: 제출·회원을 승인하고 선착순 혜택을 판단하고 결과 피드를 남기고 승인 이벤트를 낸다")
    void decideStudentId_approve() {
        // given: 대기 중인 제출과 탈퇴 신청하지 않은 회원
        StudentIdSubmission submission = pending();
        User user = user();
        given(studentIdSubmissionRepository.findWithLockBySubmissionId(submission.getSubmissionId())).willReturn(Optional.of(submission));
        given(userRepository.findWithLockById(USER_ID)).willReturn(Optional.of(user));

        // when: 승인
        adminSchoolVerificationService.decideStudentId(submission.getSubmissionId(),
                new AdminSchoolVerificationDecideStudentIdCommand(StudentIdDecision.APPROVED, null, REVIEWER));

        // then: 상태 변경 + 승인 시점에 정원 판단 + 피드 + 이벤트
        assertThat(submission.getStatus()).isEqualTo(StudentIdSubmissionStatus.APPROVED);
        assertThat(submission.getReviewer()).isEqualTo(REVIEWER);
        assertThat(user.getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.APPROVED);
        then(earlySignupGrantWriter).should().assign(any(), any(), any(), any());
        then(appNotificationFeedWriter).should().writeSchoolVerificationApproved(USER_ID);
        then(eventPublisher).should().publishEvent(new SchoolVerificationApprovedEvent(USER_ID));
    }

    @Test
    @DisplayName("반려: 사유와 검수자를 남기고 회원을 rejected 로 바꾸고 사유 피드를 남기며, 선착순 혜택은 건드리지 않는다")
    void decideStudentId_reject() {
        // given: 대기 중인 제출과 회원
        StudentIdSubmission submission = pending();
        User user = user();
        given(studentIdSubmissionRepository.findWithLockBySubmissionId(submission.getSubmissionId())).willReturn(Optional.of(submission));
        given(userRepository.findWithLockById(USER_ID)).willReturn(Optional.of(user));

        // when: 주민번호 노출로 반려
        adminSchoolVerificationService.decideStudentId(submission.getSubmissionId(),
                new AdminSchoolVerificationDecideStudentIdCommand(StudentIdDecision.REJECTED, StudentIdRejectReason.RRN_VISIBLE, REVIEWER));

        // then: 반려 기록 + 피드, 혜택·승인 이벤트 없음
        assertThat(submission.getStatus()).isEqualTo(StudentIdSubmissionStatus.REJECTED);
        assertThat(submission.getRejectReason()).isEqualTo(StudentIdRejectReason.RRN_VISIBLE);
        assertThat(user.getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.REJECTED);
        then(appNotificationFeedWriter).should().writeSchoolVerificationRejected(USER_ID, StudentIdRejectReason.RRN_VISIBLE);
        verifyNoInteractions(earlySignupGrantWriter, eventPublisher);
    }

    @Test
    @DisplayName("반려 사유 없이 반려하면 INVALID_REQUEST 로 거절하고 제출을 조회하지 않는다")
    void decideStudentId_reject_without_reason_throws() {
        // when & then: 400 예외
        assertThatThrownBy(() -> adminSchoolVerificationService.decideStudentId(UUID.randomUUID(),
                new AdminSchoolVerificationDecideStudentIdCommand(StudentIdDecision.REJECTED, null, REVIEWER)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(studentIdSubmissionRepository);
    }

    @Test
    @DisplayName("이미 판정한 제출을 다시 판정하면 STUDENT_ID_REVIEW_CONFLICT 로 거절하고 알림·혜택을 다시 실행하지 않는다")
    void decideStudentId_already_decided_conflicts() {
        // given: 다른 운영자가 먼저 승인한 제출
        StudentIdSubmission submission = pending();
        submission.approve("other@trytwinly.com");
        given(studentIdSubmissionRepository.findWithLockBySubmissionId(submission.getSubmissionId())).willReturn(Optional.of(submission));

        // when & then: 409 예외 + 부수 효과 없음
        assertThatThrownBy(() -> adminSchoolVerificationService.decideStudentId(submission.getSubmissionId(),
                new AdminSchoolVerificationDecideStudentIdCommand(StudentIdDecision.APPROVED, null, REVIEWER)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STUDENT_ID_REVIEW_CONFLICT);
        verifyNoInteractions(earlySignupGrantWriter, appNotificationFeedWriter, eventPublisher);
    }

    @Test
    @DisplayName("탈퇴 신청한 회원의 제출을 판정하면 STUDENT_ID_REVIEW_CONFLICT 로 거절한다")
    void decideStudentId_withdrawing_user_conflicts() {
        // given: 열어 둔 상세 화면에서 판정했지만 그 사이 회원이 탈퇴 신청
        StudentIdSubmission submission = pending();
        User user = user();
        user.requestWithdrawal(Duration.ofDays(14));
        given(studentIdSubmissionRepository.findWithLockBySubmissionId(submission.getSubmissionId())).willReturn(Optional.of(submission));
        given(userRepository.findWithLockById(USER_ID)).willReturn(Optional.of(user));

        // when & then: 409 예외, 제출은 그대로 대기
        assertThatThrownBy(() -> adminSchoolVerificationService.decideStudentId(submission.getSubmissionId(),
                new AdminSchoolVerificationDecideStudentIdCommand(StudentIdDecision.APPROVED, null, REVIEWER)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STUDENT_ID_REVIEW_CONFLICT);
        assertThat(submission.isPending()).isTrue();
    }

    private StudentIdSubmission pending() {
        return StudentIdSubmission.create(UUID.randomUUID(), USER_ID, "트윈리대학교", "student-id/upload/user-31/a", "student-id/review/a");
    }

    private User user() {
        User user = User.create("가온", "홍", "h1", "길동", "h2", Gender.MALE, "트윈리대학교", "oh", "aff", "ah",
                "2025", "nh", "2000-01-01", "bh", "010", "ph", null, null, "di", "dh", null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.startStudentIdVerification();
        return user;
    }
}

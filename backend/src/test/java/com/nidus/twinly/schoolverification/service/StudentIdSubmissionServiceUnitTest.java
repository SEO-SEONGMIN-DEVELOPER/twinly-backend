package com.nidus.twinly.schoolverification.service;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.service.OrganizationCatalog;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import com.nidus.twinly.schoolverification.entity.AnonSessionStudentIdSubmission;
import com.nidus.twinly.schoolverification.entity.StudentIdSubmission;
import com.nidus.twinly.schoolverification.repository.AnonSessionStudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.repository.StudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.storage.StudentIdPhotoOwner;
import com.nidus.twinly.schoolverification.storage.StudentIdPhotoStorage;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StudentIdSubmissionServiceUnitTest {

    private static final Long ANON_SESSION_ID = 7L;
    private static final Long USER_ID = 31L;
    private static final String ORGANIZATION = "트윈리대학교";
    private static final String ANON_KEY = "student-id/upload/anon-7/new";
    private static final String USER_KEY = "student-id/upload/user-31/new";

    @Mock
    OrganizationCatalog organizationCatalog;

    @Mock
    StudentIdPhotoStorage studentIdPhotoStorage;

    @Mock
    AnonSessionRepository anonSessionRepository;

    @Mock
    AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;

    @Mock
    AnonSessionStudentIdSubmissionRepository anonSessionStudentIdSubmissionRepository;

    @Mock
    StudentIdSubmissionRepository studentIdSubmissionRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    StudentIdSubmissionService studentIdSubmissionService;

    @Test
    @DisplayName("가입용 첫 제출: 업로드를 확인해 검수용으로 복사하고, 제출을 저장하고, 익명 세션 학교를 바꾸고, 이메일 인증을 지운다")
    void submitForAnonSession_first_submission_saves() {
        // given: 학교 목록에 있는 학교, 현재 제출 없음, 쓰지 않은 key
        AnonSession anonSession = anonSession();
        given(anonSessionRepository.findWithLockById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession));
        given(organizationCatalog.findByName(ORGANIZATION)).willReturn(organization());
        given(anonSessionStudentIdSubmissionRepository.findByAnonSessionIdAndReplacedAtIsNull(ANON_SESSION_ID)).willReturn(Optional.empty());
        given(anonSessionStudentIdSubmissionRepository.existsByUploadKey(ANON_KEY)).willReturn(false);
        given(studentIdPhotoStorage.copyToReview(eq(ANON_KEY), any())).willReturn("student-id/review/x");

        // when: 학생증 제출
        studentIdSubmissionService.submitForAnonSession(ANON_SESSION_ID, ORGANIZATION, ANON_KEY);

        // then: 이 세션의 업로드인지 확인한 뒤 저장, 저장한 제출의 검수용 key 는 복사한 값
        then(studentIdPhotoStorage).should().requireUploaded(StudentIdPhotoOwner.anonSession(ANON_SESSION_ID), ANON_KEY);
        ArgumentCaptor<AnonSessionStudentIdSubmission> captor = ArgumentCaptor.forClass(AnonSessionStudentIdSubmission.class);
        then(anonSessionStudentIdSubmissionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUploadKey()).isEqualTo(ANON_KEY);
        assertThat(captor.getValue().getReviewKey()).isEqualTo("student-id/review/x");
        assertThat(captor.getValue().getOrganization()).isEqualTo(ORGANIZATION);
        assertThat(captor.getValue().getSubmissionId()).isNotNull();
        assertThat(anonSession.getOrganization()).isEqualTo(ORGANIZATION);
        then(anonSessionVerificationSessionRepository).should().deleteByAnonSessionIdAndType(ANON_SESSION_ID, VerificationType.EMAIL);
    }

    @Test
    @DisplayName("가입용 재시도: 현재 제출과 key 가 같으면 사진을 복사하지 않고 제출 ID 도 바꾸지 않는다 (멱등)")
    void submitForAnonSession_same_key_is_idempotent() {
        // given: 같은 key 로 이미 제출한 세션 (응답 유실 뒤 재시도)
        AnonSessionStudentIdSubmission current = AnonSessionStudentIdSubmission.create(UUID.randomUUID(), ANON_SESSION_ID, ORGANIZATION, ANON_KEY, "student-id/review/old");
        UUID submissionId = current.getSubmissionId();
        given(anonSessionRepository.findWithLockById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession()));
        given(organizationCatalog.findByName(ORGANIZATION)).willReturn(organization());
        given(anonSessionStudentIdSubmissionRepository.findByAnonSessionIdAndReplacedAtIsNull(ANON_SESSION_ID)).willReturn(Optional.of(current));

        // when: 같은 요청 재시도
        studentIdSubmissionService.submitForAnonSession(ANON_SESSION_ID, ORGANIZATION, ANON_KEY);

        // then: 복사·저장 없음, 기존 제출이 그대로 현재 제출
        then(studentIdPhotoStorage).should(never()).copyToReview(anyString(), any());
        then(anonSessionStudentIdSubmissionRepository).should(never()).save(any());
        assertThat(current.getSubmissionId()).isEqualTo(submissionId);
        assertThat(current.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("가입용 교체: 다른 key 로 내면 새 제출을 저장하고 이전 제출은 교체 처리돼 파기 대상이 된다")
    void submitForAnonSession_new_key_replaces_current() {
        // given: 이미 다른 사진으로 제출한 세션
        AnonSessionStudentIdSubmission current = AnonSessionStudentIdSubmission.create(UUID.randomUUID(), ANON_SESSION_ID, ORGANIZATION, "student-id/upload/anon-7/old", "student-id/review/old");
        given(anonSessionRepository.findWithLockById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession()));
        given(organizationCatalog.findByName(ORGANIZATION)).willReturn(organization());
        given(anonSessionStudentIdSubmissionRepository.findByAnonSessionIdAndReplacedAtIsNull(ANON_SESSION_ID)).willReturn(Optional.of(current));
        given(anonSessionStudentIdSubmissionRepository.existsByUploadKey(ANON_KEY)).willReturn(false);
        given(studentIdPhotoStorage.copyToReview(eq(ANON_KEY), any())).willReturn("student-id/review/new");

        // when: 새 사진으로 제출
        studentIdSubmissionService.submitForAnonSession(ANON_SESSION_ID, ORGANIZATION, ANON_KEY);

        // then: 이전 제출은 교체됨, 새 제출 저장
        assertThat(current.isCurrent()).isFalse();
        then(anonSessionStudentIdSubmissionRepository).should().save(any(AnonSessionStudentIdSubmission.class));
    }

    @Test
    @DisplayName("가입용 교체 실패: 새 사진 복사가 실패하면 이전 제출을 그대로 유지한다")
    void submitForAnonSession_copy_failure_keeps_current() {
        // given: 이전 제출이 있고 S3 복사가 실패
        AnonSessionStudentIdSubmission current = AnonSessionStudentIdSubmission.create(UUID.randomUUID(), ANON_SESSION_ID, ORGANIZATION, "student-id/upload/anon-7/old", "student-id/review/old");
        given(anonSessionRepository.findWithLockById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession()));
        given(organizationCatalog.findByName(ORGANIZATION)).willReturn(organization());
        given(anonSessionStudentIdSubmissionRepository.findByAnonSessionIdAndReplacedAtIsNull(ANON_SESSION_ID)).willReturn(Optional.of(current));
        given(anonSessionStudentIdSubmissionRepository.existsByUploadKey(ANON_KEY)).willReturn(false);
        willThrow(new IllegalStateException("S3 down")).given(studentIdPhotoStorage).copyToReview(eq(ANON_KEY), any());

        // when & then: 예외가 올라가고 이전 제출은 현재 제출로 남는다
        assertThatThrownBy(() -> studentIdSubmissionService.submitForAnonSession(ANON_SESSION_ID, ORGANIZATION, ANON_KEY))
                .isInstanceOf(IllegalStateException.class);
        assertThat(current.isCurrent()).isTrue();
        then(anonSessionStudentIdSubmissionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("가입용 제출: 이미 교체된 key 를 다시 내면 STUDENT_ID_PHOTO_NOT_FOUND 로 거절한다")
    void submitForAnonSession_reused_key_throws() {
        // given: 예전에 쓰고 교체된 key
        given(anonSessionRepository.findWithLockById(ANON_SESSION_ID)).willReturn(Optional.of(anonSession()));
        given(organizationCatalog.findByName(ORGANIZATION)).willReturn(organization());
        given(anonSessionStudentIdSubmissionRepository.findByAnonSessionIdAndReplacedAtIsNull(ANON_SESSION_ID)).willReturn(Optional.empty());
        given(anonSessionStudentIdSubmissionRepository.existsByUploadKey(ANON_KEY)).willReturn(true);

        // when & then: 422 예외, 복사하지 않음
        assertThatThrownBy(() -> studentIdSubmissionService.submitForAnonSession(ANON_SESSION_ID, ORGANIZATION, ANON_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STUDENT_ID_PHOTO_NOT_FOUND);
        then(studentIdPhotoStorage).should(never()).copyToReview(anyString(), any());
    }

    @Test
    @DisplayName("가입 이관: 현재 제출은 PENDING, 교체된 제출은 REPLACED 로 같은 제출 ID 를 유지해 회원 쪽에 옮기고 익명 쪽은 지운다")
    void transferToUser_moves_rows_with_same_submission_id() {
        // given: 교체된 제출 하나와 현재 제출 하나
        AnonSessionStudentIdSubmission replaced = AnonSessionStudentIdSubmission.create(UUID.randomUUID(), ANON_SESSION_ID, ORGANIZATION, "k1", "r1");
        replaced.replace();
        AnonSessionStudentIdSubmission current = AnonSessionStudentIdSubmission.create(UUID.randomUUID(), ANON_SESSION_ID, ORGANIZATION, "k2", "r2");
        List<AnonSessionStudentIdSubmission> rows = List.of(replaced, current);
        given(anonSessionStudentIdSubmissionRepository.findAllByAnonSessionIdOrderByIdAsc(ANON_SESSION_ID)).willReturn(rows);

        // when: 회원으로 이관
        studentIdSubmissionService.transferToUser(ANON_SESSION_ID, USER_ID);

        // then: 두 행 모두 회원 쪽으로, 상태와 제출 ID 유지 + 익명 행 삭제
        ArgumentCaptor<StudentIdSubmission> captor = ArgumentCaptor.forClass(StudentIdSubmission.class);
        then(studentIdSubmissionRepository).should(times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(StudentIdSubmission::getStatus)
                .containsExactly(StudentIdSubmissionStatus.REPLACED, StudentIdSubmissionStatus.PENDING);
        assertThat(captor.getAllValues()).extracting(StudentIdSubmission::getSubmissionId)
                .containsExactly(replaced.getSubmissionId(), current.getSubmissionId());
        assertThat(captor.getAllValues()).extracting(StudentIdSubmission::getUserId).containsOnly(USER_ID);
        then(anonSessionStudentIdSubmissionRepository).should().deleteAll(rows);
    }

    @Test
    @DisplayName("반려 뒤 재제출: 새 사진을 가입 학교로 PENDING 제출로 저장하고 회원 상태를 pending 으로 바꾼다")
    void resubmit_after_rejection_saves_pending() {
        // given: 반려된 회원, 마지막 제출과 다른 key
        User user = user(SchoolVerificationStatus.REJECTED);
        given(userRepository.findWithLockById(USER_ID)).willReturn(Optional.of(user));
        given(studentIdSubmissionRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
                .willReturn(Optional.of(StudentIdSubmission.create(UUID.randomUUID(), USER_ID, ORGANIZATION, "student-id/upload/user-31/old", "r")));
        given(studentIdSubmissionRepository.existsByUploadKey(USER_KEY)).willReturn(false);
        given(studentIdPhotoStorage.copyToReview(eq(USER_KEY), any())).willReturn("student-id/review/new");

        // when: 재제출
        studentIdSubmissionService.resubmit(USER_ID, USER_KEY);

        // then: 회원 소유 업로드 확인, 가입 학교로 새 제출 저장, 상태 pending
        then(studentIdPhotoStorage).should().requireUploaded(StudentIdPhotoOwner.user(USER_ID), USER_KEY);
        ArgumentCaptor<StudentIdSubmission> captor = ArgumentCaptor.forClass(StudentIdSubmission.class);
        then(studentIdSubmissionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StudentIdSubmissionStatus.PENDING);
        assertThat(captor.getValue().getOrganization()).isEqualTo(ORGANIZATION);
        assertThat(user.getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.PENDING);
    }

    @Test
    @DisplayName("반려 뒤 재제출 재시도: 마지막 제출과 key 가 같으면 그 사이 판정됐어도 상태를 바꾸지 않고 200 으로 끝낸다")
    void resubmit_same_key_after_decision_is_idempotent() {
        // given: 재제출 직후 승인까지 된 회원이 같은 key 로 재시도
        User user = user(SchoolVerificationStatus.APPROVED);
        given(userRepository.findWithLockById(USER_ID)).willReturn(Optional.of(user));
        given(studentIdSubmissionRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
                .willReturn(Optional.of(StudentIdSubmission.create(UUID.randomUUID(), USER_ID, ORGANIZATION, USER_KEY, "r")));

        // when: 같은 key 로 재시도
        studentIdSubmissionService.resubmit(USER_ID, USER_KEY);

        // then: 저장 없음, 승인 상태 유지
        then(studentIdSubmissionRepository).should(never()).save(any());
        assertThat(user.getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.APPROVED);
    }

    @Test
    @DisplayName("반려 상태가 아닌 회원이 새 key 로 재제출하면 SCHOOL_VERIFICATION_NOT_REJECTED 로 거절한다")
    void resubmit_when_pending_throws() {
        // given: 검수 대기 중인 회원
        given(userRepository.findWithLockById(USER_ID)).willReturn(Optional.of(user(SchoolVerificationStatus.PENDING)));
        given(studentIdSubmissionRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
                .willReturn(Optional.of(StudentIdSubmission.create(UUID.randomUUID(), USER_ID, ORGANIZATION, "student-id/upload/user-31/old", "r")));

        // when & then: 409 예외, 저장 없음
        assertThatThrownBy(() -> studentIdSubmissionService.resubmit(USER_ID, USER_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHOOL_VERIFICATION_NOT_REJECTED);
        then(studentIdSubmissionRepository).should(never()).save(any());
    }

    private AnonSession anonSession() {
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(anonSession, "id", ANON_SESSION_ID);
        return anonSession;
    }

    private Organization organization() {
        Organization organization = BeanUtils.instantiateClass(Organization.class);
        ReflectionTestUtils.setField(organization, "id", 1L);
        ReflectionTestUtils.setField(organization, "name", ORGANIZATION);
        return organization;
    }

    private User user(SchoolVerificationStatus status) {
        User user = User.create("nick", "홍", "h1", "길동", "h2", Gender.MALE, ORGANIZATION, "oh", "aff", "ah",
                "2025", "nh", "2000-01-01", "bh", "010", "ph", null, null, "di", "dh", null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "schoolVerificationStatus", status);
        return user;
    }
}

package com.nidus.twinly.schoolverification.integration;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.entity.AnonSessionAgreement;
import com.nidus.twinly.anon.entity.AnonSessionPhoto;
import com.nidus.twinly.anon.repository.AnonSessionAgreementRepository;
import com.nidus.twinly.anon.repository.AnonSessionPhotoRepository;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.anon.service.ExpiredAnonSessionCleanupService;
import com.nidus.twinly.auth.entity.AnonSessionIdentityVerification;
import com.nidus.twinly.auth.entity.AnonSessionVerificationSession;
import com.nidus.twinly.auth.repository.AnonSessionIdentityVerificationRepository;
import com.nidus.twinly.auth.repository.AnonSessionVerificationSessionRepository;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.domain.MobileCarrier;
import com.nidus.twinly.common.domain.NationalInfo;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.purchase.repository.EarlySignupGrantRepository;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationMethod;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import com.nidus.twinly.schoolverification.entity.AnonSessionStudentIdSubmission;
import com.nidus.twinly.schoolverification.entity.StudentIdSubmission;
import com.nidus.twinly.schoolverification.repository.AnonSessionStudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.repository.StudentIdSubmissionRepository;
import com.nidus.twinly.schoolverification.service.StudentIdPhotoPurgeService;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.service.WithdrawnUserChunkDeletionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentIdIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZATION = "성균관대학교";
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "test-admin-token";
    private static final String REVIEWER = "ops@trytwinly.com";

    @Autowired
    AnonSessionRepository anonSessionRepository;

    @Autowired
    AnonSessionIdentityVerificationRepository anonSessionIdentityVerificationRepository;

    @Autowired
    AnonSessionVerificationSessionRepository anonSessionVerificationSessionRepository;

    @Autowired
    AnonSessionAgreementRepository anonSessionAgreementRepository;

    @Autowired
    AnonSessionPhotoRepository anonSessionPhotoRepository;

    @Autowired
    AnonSessionStudentIdSubmissionRepository anonSessionStudentIdSubmissionRepository;

    @Autowired
    StudentIdSubmissionRepository studentIdSubmissionRepository;

    @Autowired
    EarlySignupGrantRepository earlySignupGrantRepository;

    @Autowired
    StudentIdPhotoPurgeService studentIdPhotoPurgeService;

    @Autowired
    ExpiredAnonSessionCleanupService expiredAnonSessionCleanupService;

    @Autowired
    WithdrawnUserChunkDeletionService withdrawnUserChunkDeletionService;

    @Autowired
    BlindIndexHasher blindIndexHasher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final AtomicInteger seq = new AtomicInteger();

    @Test
    @DisplayName("학교 목록: 기존 organizations 는 그대로 두고 studentIdOrganizations 를 빈 배열로 준다")
    void organizations_has_empty_student_id_organizations() throws Exception {
        // when: 인증 없이 학교 목록 조회
        var result = mockMvc.perform(get("/api/v1/onboarding/organizations"));

        // then: 기존 목록의 학교는 모두 도메인이 1개 이상, 새 필드는 빈 배열
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations[0].domains[0]").exists())
                .andExpect(jsonPath("$.studentIdOrganizations").isArray())
                .andExpect(jsonPath("$.studentIdOrganizations").isEmpty());
    }

    @Test
    @DisplayName("가입용 학생증: 발급한 key 로 제출하면 검수용으로 복사해 저장하고, 이메일 인증을 지우고, 학교 학과 목록을 열어 준다")
    void onboarding_presign_and_submit_end_to_end() throws Exception {
        // given: 이메일 인증까지 해 둔 익명 세션, S3 는 업로드 완료 상태로 응답
        AnonSession anonSession = savedAnonSession(Instant.now().plus(Duration.ofDays(1)));
        verifiedEmail(anonSession.getId());
        given(s3Service.presignPut(anyString(), eq("image/jpeg"), any())).willReturn("https://s3/upload");
        given(s3Service.contentLength(anyString())).willReturn(Optional.of(2_000L));

        // when: 업로드 URL 발급
        String presignBody = mockMvc.perform(post("/api/v1/auth/onboarding/student-id/presign")
                        .header("Authorization", "Bearer " + anonSession.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType":"image/jpeg"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key", startsWith("student-id/upload/anon-" + anonSession.getId() + "/")))
                .andReturn().getResponse().getContentAsString();
        String key = presignBody.replaceAll(".*\"key\":\"([^\"]+)\".*", "$1");

        // when: 발급받은 key 로 학생증 제출
        mockMvc.perform(post("/api/v1/auth/onboarding/student-id")
                        .header("Authorization", "Bearer " + anonSession.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"%s","key":"%s"}
                                """.formatted(ORGANIZATION, key)))
                .andExpect(status().isOk());

        // then: 현재 제출이 저장되고 검수용 경로로 복사됨, 익명 세션 학교가 바뀌고 이메일 인증은 지워짐
        AnonSessionStudentIdSubmission submission = anonSessionStudentIdSubmissionRepository
                .findByAnonSessionIdAndReplacedAtIsNull(anonSession.getId()).orElseThrow();
        assertThat(submission.getUploadKey()).isEqualTo(key);
        assertThat(submission.getReviewKey()).isEqualTo("student-id/review/" + submission.getSubmissionId());
        then(s3Service).should().copy(key, submission.getReviewKey());
        assertThat(anonSessionRepository.findById(anonSession.getId()).orElseThrow().getOrganization()).isEqualTo(ORGANIZATION);
        assertThat(anonSessionVerificationSessionRepository.findByAnonSessionIdAndType(anonSession.getId(), VerificationType.EMAIL)).isEmpty();

        // then: 이메일 인증 없이도 학과 목록이 열린다
        mockMvc.perform(get("/api/v1/onboarding/affiliations")
                        .header("Authorization", "Bearer " + anonSession.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliations[0]").exists());
    }

    @Test
    @DisplayName("가입용 학생증: 다른 세션의 key 로 제출하면 422 STUDENT_ID_PHOTO_NOT_FOUND 이고 제출이 남지 않는다")
    void onboarding_submit_with_other_session_key_returns_422() throws Exception {
        // given: 익명 세션 하나와 다른 세션 소유의 key
        AnonSession anonSession = savedAnonSession(Instant.now().plus(Duration.ofDays(1)));

        // when: 남의 key 로 제출
        var result = mockMvc.perform(post("/api/v1/auth/onboarding/student-id")
                .header("Authorization", "Bearer " + anonSession.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"organizationName":"%s","key":"student-id/upload/anon-999999/x"}
                        """.formatted(ORGANIZATION)));

        // then: 422 + 저장 없음
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STUDENT_ID_PHOTO_NOT_FOUND"));
        assertThat(anonSessionStudentIdSubmissionRepository.findByAnonSessionIdAndReplacedAtIsNull(anonSession.getId())).isEmpty();
    }

    @Test
    @DisplayName("학생증 가입: 이메일 없이 가입되고 학교 인증 pending 으로 시작하며, 같은 제출 ID 로 검수 대기열에 들어가고 선착순은 배정하지 않는다. 승인 전 시즌 참여는 403 이다")
    void signup_with_student_id_then_status_and_participation_blocked() throws Exception {
        // given: 본인인증·약관·프로필을 마치고 학생증을 낸 익명 세션
        AnonSession anonSession = readyToSignUp();
        AnonSessionStudentIdSubmission anonSubmission = anonSessionStudentIdSubmissionRepository.save(AnonSessionStudentIdSubmission.create(
                UUID.randomUUID(), anonSession.getId(), ORGANIZATION, "student-id/upload/anon-" + anonSession.getId() + "/a", "student-id/review/a"));

        // when: 회원가입
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer " + anonSession.getToken()))
                .andExpect(status().isCreated());

        // then: 이메일 없이 학생증 방식 pending, 학교는 제출한 학교
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE nickname = ?", Long.class, anonSession.getNickname());
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getOrganization()).isEqualTo(ORGANIZATION);
        assertThat(user.getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.PENDING);
        assertThat(user.getSchoolVerificationMethod()).isEqualTo(SchoolVerificationMethod.STUDENT_ID);

        // then: 제출 ID 그대로 회원 쪽으로 이관, 익명 쪽은 비어 있음, 선착순 배정 없음
        StudentIdSubmission submission = studentIdSubmissionRepository.findFirstByUserIdOrderByIdDesc(user.getId()).orElseThrow();
        assertThat(submission.getSubmissionId()).isEqualTo(anonSubmission.getSubmissionId());
        assertThat(submission.getStatus()).isEqualTo(StudentIdSubmissionStatus.PENDING);
        assertThat(anonSessionStudentIdSubmissionRepository.findAllByAnonSessionIdOrderByIdAsc(anonSession.getId())).isEmpty();
        assertThat(earlySignupGrantRepository.findByUserIdAndGrantedAtIsNull(user.getId())).isEmpty();

        // then: 내 상태에 학교 인증이 pending·studentId 로 나간다
        mockMvc.perform(get("/api/v1/me/status").header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolVerification.status").value("pending"))
                .andExpect(jsonPath("$.schoolVerification.method").value("studentId"))
                .andExpect(jsonPath("$.schoolVerification.rejectReason").isEmpty());

        // then: 승인 전에는 시즌 참여를 서버가 막는다
        mockMvc.perform(put("/api/v1/season/participation").header("Authorization", bearer(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHOOL_VERIFICATION_NOT_APPROVED"));
    }

    @Test
    @DisplayName("기존 이메일 회원: 내 상태의 학교 인증은 approved·email 이다")
    void email_member_status_is_approved() throws Exception {
        // given: 기존 방식으로 가입한 회원
        User user = saveUser();

        // when & then: approved·email·사유 없음
        mockMvc.perform(get("/api/v1/me/status").header("Authorization", bearer(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolVerification.status").value("approved"))
                .andExpect(jsonPath("$.schoolVerification.method").value("email"))
                .andExpect(jsonPath("$.schoolVerification.rejectReason").isEmpty());
    }

    @Test
    @DisplayName("어드민 검수: 대기열·상세를 거쳐 반려하면 회원이 rejected 가 되고 사유 피드가 남으며, 같은 제출을 다시 판정하면 409 다")
    void admin_review_reject_then_conflict() throws Exception {
        // given: 학생증으로 가입해 대기 중인 회원
        User user = pendingStudentIdUser();
        StudentIdSubmission submission = studentIdSubmissionRepository.save(StudentIdSubmission.create(
                UUID.randomUUID(), user.getId(), ORGANIZATION, "student-id/upload/user-" + user.getId() + "/a", "student-id/review/a"));
        given(s3Service.presignGet(eq("student-id/review/a"), any())).willReturn("https://s3/review/a");

        // when & then: 대기열에 보인다
        mockMvc.perform(get("/admin/school-verifications/student-ids").header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.submissionId == '%s')]".formatted(submission.getSubmissionId())).exists());

        // when & then: 상세에 사진 주소와 고른 학교가 나온다
        mockMvc.perform(get("/admin/school-verifications/student-ids/{id}", submission.getSubmissionId()).header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://s3/review/a"))
                .andExpect(jsonPath("$.organization").value(ORGANIZATION))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // when: 반려
        mockMvc.perform(post("/admin/school-verifications/student-ids/{id}/decision", submission.getSubmissionId())
                        .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECTED","rejectReason":"NAME_MISMATCH","reviewer":"%s"}
                                """.formatted(REVIEWER)))
                .andExpect(status().isOk());

        // then: 제출·회원 상태 변경, 결과 피드 저장(대상 없는 피드라 CHECK 제약을 통과해야 한다)
        StudentIdSubmission decided = studentIdSubmissionRepository.findBySubmissionId(submission.getSubmissionId()).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(StudentIdSubmissionStatus.REJECTED);
        assertThat(decided.getRejectReason()).isEqualTo(StudentIdRejectReason.NAME_MISMATCH);
        assertThat(decided.getReviewer()).isEqualTo(REVIEWER);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.REJECTED);
        Map<String, Object> feed = jdbcTemplate.queryForMap(
                "SELECT type, title, body, target_kind FROM app_notification_feeds WHERE user_id = ?", user.getId());
        assertThat(feed.get("type")).isEqualTo("SCHOOL_VERIFICATION");
        assertThat(feed.get("title")).isEqualTo("학생증을 다시 확인해 주세요");
        assertThat(feed.get("body")).isEqualTo(StudentIdRejectReason.NAME_MISMATCH.message());
        assertThat(feed.get("target_kind")).isNull();

        // then: 목록에서 대기 상태로는 빠지고 반려 상태로는 검수자와 함께 보인다
        mockMvc.perform(get("/admin/school-verifications/student-ids").header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN).param("status", "PENDING"))
                .andExpect(jsonPath("$.items[?(@.submissionId == '%s')]".formatted(submission.getSubmissionId())).doesNotExist());
        mockMvc.perform(get("/admin/school-verifications/student-ids").header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN).param("status", "REJECTED"))
                .andExpect(jsonPath("$.items[?(@.submissionId == '%s')].status".formatted(submission.getSubmissionId())).value("REJECTED"))
                .andExpect(jsonPath("$.items[?(@.submissionId == '%s')].reviewer".formatted(submission.getSubmissionId())).value(REVIEWER));

        // then: 내 상태에 반려 사유가 나간다
        mockMvc.perform(get("/api/v1/me/status").header("Authorization", bearer(user.getId())))
                .andExpect(jsonPath("$.schoolVerification.status").value("rejected"))
                .andExpect(jsonPath("$.schoolVerification.rejectReason").value("NAME_MISMATCH"));

        // when & then: 오래된 화면에서 다시 승인하면 409
        mockMvc.perform(post("/admin/school-verifications/student-ids/{id}/decision", submission.getSubmissionId())
                        .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","rejectReason":null,"reviewer":"%s"}
                                """.formatted(REVIEWER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ID_REVIEW_CONFLICT"));
    }

    @Test
    @DisplayName("어드민 승인: 회원이 approved 가 되고 같은 성별 선착순 자리를 승인 시점에 배정한다")
    void admin_review_approve_assigns_early_grant() throws Exception {
        // given: 대기 중인 학생증 회원
        User user = pendingStudentIdUser();
        StudentIdSubmission submission = studentIdSubmissionRepository.save(StudentIdSubmission.create(
                UUID.randomUUID(), user.getId(), ORGANIZATION, "student-id/upload/user-" + user.getId() + "/a", "student-id/review/a"));

        // when: 승인
        mockMvc.perform(post("/admin/school-verifications/student-ids/{id}/decision", submission.getSubmissionId())
                        .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","rejectReason":null,"reviewer":"%s"}
                                """.formatted(REVIEWER)))
                .andExpect(status().isOk());

        // then: 승인 상태 + 선착순 배정 행 생성(부여는 커밋 뒤 리스너 몫이라 아직 미부여)
        assertThat(userRepository.findById(user.getId()).orElseThrow().getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.APPROVED);
        assertThat(earlySignupGrantRepository.findByUserIdAndGrantedAtIsNull(user.getId())).isPresent();
    }

    @Test
    @DisplayName("반려 뒤 재제출: 새 key 로 내면 새 PENDING 제출이 생기고 회원이 pending 으로 돌아가며, 같은 key 재시도는 행을 더 만들지 않는다")
    void resubmit_after_rejection_end_to_end() throws Exception {
        // given: 반려된 학생증 회원, 새 사진은 업로드 완료
        User user = pendingStudentIdUser();
        StudentIdSubmission rejected = StudentIdSubmission.create(UUID.randomUUID(), user.getId(), ORGANIZATION, "student-id/upload/user-" + user.getId() + "/old", "student-id/review/old");
        rejected.reject(StudentIdRejectReason.BLURRY, REVIEWER);
        studentIdSubmissionRepository.save(rejected);
        user.rejectSchoolVerification();
        userRepository.save(user);
        String key = "student-id/upload/user-" + user.getId() + "/new";
        given(s3Service.contentLength(key)).willReturn(Optional.of(3_000L));

        // when: 재제출 두 번(응답 유실 뒤 재시도)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/me/school-verification/student-id")
                            .header("Authorization", bearer(user.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"key":"%s"}
                                    """.formatted(key)))
                    .andExpect(status().isOk());
        }

        // then: 제출은 2건(반려 1 + 새 대기 1), 회원은 pending
        List<StudentIdSubmission> submissions = studentIdSubmissionRepository.findAllByUserIdOrderByIdDesc(user.getId());
        assertThat(submissions).hasSize(2);
        assertThat(submissions.getFirst().getStatus()).isEqualTo(StudentIdSubmissionStatus.PENDING);
        assertThat(submissions.getFirst().getUploadKey()).isEqualTo(key);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getSchoolVerificationStatus()).isEqualTo(SchoolVerificationStatus.PENDING);
    }

    @Test
    @DisplayName("재제출: 인증 헤더가 없으면 401 이다")
    void resubmit_without_auth_returns_401() throws Exception {
        // when & then: 인증 없이 재제출
        mockMvc.perform(post("/api/v1/me/school-verification/student-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"student-id/upload/user-1/x"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사진 파기 배치: 판정된 제출의 업로드·검수용 사진을 지우고 photo_deleted_at 을 남기며, 대기 중 제출은 건드리지 않는다")
    void purge_deletes_decided_photos_only() {
        // given: 반려된 제출 하나와 대기 중인 제출 하나
        User user = pendingStudentIdUser();
        StudentIdSubmission rejected = StudentIdSubmission.create(UUID.randomUUID(), user.getId(), ORGANIZATION, "student-id/upload/u/old", "student-id/review/old");
        rejected.reject(StudentIdRejectReason.BLURRY, REVIEWER);
        studentIdSubmissionRepository.save(rejected);
        StudentIdSubmission pending = studentIdSubmissionRepository.save(StudentIdSubmission.create(
                UUID.randomUUID(), user.getId(), ORGANIZATION, "student-id/upload/u/new", "student-id/review/new"));

        // when: 매시간 파기 배치 실행
        studentIdPhotoPurgeService.purge();

        // then: 판정된 제출만 S3 에서 지우고 기록
        then(s3Service).should().delete("student-id/review/old");
        then(s3Service).should().delete("student-id/upload/u/old");
        then(s3Service).should(never()).delete("student-id/review/new");
        assertThat(photoDeletedAt(rejected.getId())).isNotNull();
        assertThat(photoDeletedAt(pending.getId())).isNull();
    }

    @Test
    @DisplayName("만료된 익명 세션 정리: 학생증·프로필 사진을 S3 에서 지우고 세션과 딸린 행을 모두 지우며, 만료 전 세션은 남긴다")
    void expired_anon_session_cleanup() {
        // given: 학생증·사진·약관 동의가 있는 만료 세션과 아직 유효한 세션
        AnonSession expired = savedAnonSession(Instant.now().minus(Duration.ofDays(1)));
        anonSessionStudentIdSubmissionRepository.save(AnonSessionStudentIdSubmission.create(
                UUID.randomUUID(), expired.getId(), ORGANIZATION, "student-id/upload/anon-x/a", "student-id/review/anon-a"));
        anonSessionPhotoRepository.save(AnonSessionPhoto.create(expired.getId(), PhotoType.PROFILE, "profile/" + expired.getId() + "/p", 0, 0, 10, 10));
        policyCatalog.loadRequiredPolicyIds(PolicyKind.ONBOARDING).forEach(policyId ->
                anonSessionAgreementRepository.save(AnonSessionAgreement.create(expired.getId(), policyId, Instant.now())));
        AnonSession alive = savedAnonSession(Instant.now().plus(Duration.ofDays(1)));

        // when: 매일 정리 배치 실행
        expiredAnonSessionCleanupService.deleteExpired();

        // then: 만료 세션의 파일·행 삭제, 유효한 세션은 그대로
        then(s3Service).should().delete("student-id/review/anon-a");
        then(s3Service).should().delete("student-id/upload/anon-x/a");
        then(s3Service).should().delete("profile/" + expired.getId() + "/p");
        assertThat(anonSessionRepository.findById(expired.getId())).isEmpty();
        assertThat(anonSessionStudentIdSubmissionRepository.findAllByAnonSessionIdOrderByIdAsc(expired.getId())).isEmpty();
        assertThat(anonSessionAgreementRepository.findAllByAnonSessionId(expired.getId())).isEmpty();
        assertThat(anonSessionRepository.findById(alive.getId())).isPresent();
    }

    @Test
    @DisplayName("어드민 목록: 가입 중 교체된 제출은 상태 필터와 상관없이 빠지고, 최신 제출 상세의 이전 제출에만 나온다")
    void admin_list_excludes_replaced_but_detail_history_keeps_it() throws Exception {
        // given: 가입 중 교체된 제출과 지금 대기 중인 제출이 있는 회원
        User user = pendingStudentIdUser();
        StudentIdSubmission replaced = StudentIdSubmission.create(
                UUID.randomUUID(), user.getId(), ORGANIZATION, "student-id/upload/u/r1", "student-id/review/r1");
        ReflectionTestUtils.setField(replaced, "status", StudentIdSubmissionStatus.REPLACED);
        studentIdSubmissionRepository.save(replaced);
        StudentIdSubmission current = studentIdSubmissionRepository.save(StudentIdSubmission.create(
                UUID.randomUUID(), user.getId(), ORGANIZATION, "student-id/upload/u/r2", "student-id/review/r2"));

        // when & then: 전체 목록에는 대기 건만 보이고 교체 건은 없다
        mockMvc.perform(get("/admin/school-verifications/student-ids").header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(jsonPath("$.items[?(@.submissionId == '%s')]".formatted(current.getSubmissionId())).exists())
                .andExpect(jsonPath("$.items[?(@.submissionId == '%s')]".formatted(replaced.getSubmissionId())).doesNotExist());

        // when & then: REPLACED 로 직접 걸러도 나오지 않는다
        mockMvc.perform(get("/admin/school-verifications/student-ids").header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN).param("status", "REPLACED"))
                .andExpect(jsonPath("$.items[?(@.submissionId == '%s')]".formatted(replaced.getSubmissionId())).doesNotExist());

        // when & then: 대기 건 상세의 이전 제출에는 교체 건이 남아 있다
        mockMvc.perform(get("/admin/school-verifications/student-ids/{id}", current.getSubmissionId()).header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(jsonPath("$.history[?(@.submissionId == '%s')].status".formatted(replaced.getSubmissionId())).value("REPLACED"));
    }

    @Test
    @DisplayName("탈퇴 확정: 판정 전 제출은 CANCELED 가 되어 파기 대상이 되고, 탈퇴 신청 중인 회원은 검수 대기열에서 빠진다")
    void withdrawal_cancels_pending_submission() throws Exception {
        // given: 대기 중 제출이 있는 회원이 탈퇴 신청(유예 기간이 이미 지난 상태)
        User user = pendingStudentIdUser();
        StudentIdSubmission submission = studentIdSubmissionRepository.save(StudentIdSubmission.create(
                UUID.randomUUID(), user.getId(), ORGANIZATION, "student-id/upload/u/w", "student-id/review/w"));
        user.requestWithdrawal(Duration.ZERO);
        userRepository.save(user);

        // then: 유예 중에도 대기열에서 빠진다
        mockMvc.perform(get("/admin/school-verifications/student-ids").header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN))
                .andExpect(jsonPath("$.items[?(@.submissionId == '%s')]".formatted(submission.getSubmissionId())).doesNotExist());

        // when: 탈퇴 확정 배치 실행
        withdrawnUserChunkDeletionService.deleteChunk(Instant.now().plusSeconds(1), 500);

        // then: 제출은 CANCELED → 파기 배치 대상
        assertThat(studentIdSubmissionRepository.findById(submission.getId()).orElseThrow().getStatus())
                .isEqualTo(StudentIdSubmissionStatus.CANCELED);
    }

    private Object photoDeletedAt(Long id) {
        return jdbcTemplate.queryForObject("SELECT photo_deleted_at FROM student_id_submissions WHERE id = ?", Object.class, id);
    }

    private AnonSession savedAnonSession(Instant expiresAt) {
        return anonSessionRepository.save(AnonSession.create(UUID.randomUUID(), expiresAt));
    }

    private void verifiedEmail(Long anonSessionId) {
        AnonSessionVerificationSession email = AnonSessionVerificationSession.create(
                VerificationType.EMAIL, anonSessionId, "student" + seq.incrementAndGet() + "@skku.edu", "123456", Instant.now().plusSeconds(300));
        email.verify();
        anonSessionVerificationSessionRepository.save(email);
    }

    private AnonSession readyToSignUp() {
        int n = seq.incrementAndGet();
        AnonSession anonSession = AnonSession.create(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(1)));
        anonSession.changeNickname("student-id-" + n + "-" + UUID.randomUUID().toString().substring(0, 6));
        anonSession.changeFamilyName("홍");
        anonSession.changeGivenName("길동");
        anonSession.changeOrganization(ORGANIZATION);
        anonSession.changeAffiliation("경영학과");
        anonSession.changeAffiliationNumber("2025%04d".formatted(n));
        anonSessionRepository.save(anonSession);

        String phone = "0108%07d".formatted(n);
        String di = "student-id-di-" + UUID.randomUUID();
        AnonSessionIdentityVerification identity = AnonSessionIdentityVerification.create(
                anonSession.getId(), "TWINLY-" + UUID.randomUUID(), "tx-" + UUID.randomUUID(), Instant.now().plus(Duration.ofMinutes(10)));
        identity.verify("홍길동", "2000-01-01", Gender.MALE, phone, di, blindIndexHasher.hash(di), NationalInfo.DOMESTIC, MobileCarrier.SKT);
        anonSessionIdentityVerificationRepository.save(identity);

        policyCatalog.loadRequiredPolicyIds(PolicyKind.ONBOARDING).forEach(policyId ->
                anonSessionAgreementRepository.save(AnonSessionAgreement.create(anonSession.getId(), policyId, Instant.now())));

        return anonSession;
    }

    private User pendingStudentIdUser() {
        User user = saveUser();
        ReflectionTestUtils.setField(user, "diHash", "student-id-dh-" + UUID.randomUUID());
        user.startStudentIdVerification();
        return userRepository.save(user);
    }
}

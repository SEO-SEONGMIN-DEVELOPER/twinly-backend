package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.dto.command.AdminSchoolVerificationDecideStudentIdCommand;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdDetailHistoryItemResult;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdDetailResult;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdsItemResult;
import com.nidus.twinly.admin.service.AdminSchoolVerificationService;
import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.schoolverification.domain.StudentIdDecision;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSchoolVerificationController.class)
@Import(SecurityConfig.class)
class AdminSchoolVerificationControllerUnitTest {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "test-admin-token";
    private static final UUID SUBMISSION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-23T01:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminSchoolVerificationService adminSchoolVerificationService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("검수 대기 목록 조회 성공 시 200과 페이지를 반환하고 userId 는 문자열로 나간다")
    void studentIds_success() throws Exception {
        // given: 대기 중인 제출 1건
        given(adminSchoolVerificationService.studentIds(0, 20)).willReturn(new AdminPageResult<>(
                List.of(new AdminSchoolVerificationStudentIdsItemResult(SUBMISSION_ID, 31L, "가온", "트윈리대학교", SUBMITTED_AT, 90L)),
                0, 20, 1));

        // when: 기본 페이지로 대기 목록 조회
        var result = mockMvc.perform(get("/admin/school-verifications/student-ids")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 200 + 항목·페이지 정보
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].submissionId").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$.items[0].userId").value("31"))
                .andExpect(jsonPath("$.items[0].nickname").value("가온"))
                .andExpect(jsonPath("$.items[0].elapsedMinutes").value(90))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName("어드민 토큰이 없으면 대기 목록 조회는 401을 반환하고 서비스를 호출하지 않는다")
    void studentIds_without_admin_token_returns_401() throws Exception {
        // when: 어드민 토큰 없이 대기 목록 조회
        var result = mockMvc.perform(get("/admin/school-verifications/student-ids"));

        // then: 401 + 서비스 미호출
        result.andExpect(status().isUnauthorized());
        verifyNoInteractions(adminSchoolVerificationService);
    }

    @Test
    @DisplayName("size 가 100을 넘으면 400을 반환한다")
    void studentIds_size_over_max_returns_400() throws Exception {
        // when: size=101 로 조회
        var result = mockMvc.perform(get("/admin/school-verifications/student-ids")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .param("size", "101"));

        // then: 400 + 서비스 미호출
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(adminSchoolVerificationService);
    }

    @Test
    @DisplayName("검수 상세 조회 성공 시 사진 주소·본인인증 성명·이전 반려 이력을 반환한다")
    void studentIdDetail_success() throws Exception {
        // given: 한 번 반려된 뒤 다시 낸 대기 중 제출
        UUID previousId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        given(adminSchoolVerificationService.studentIdDetail(SUBMISSION_ID)).willReturn(new AdminSchoolVerificationStudentIdDetailResult(
                SUBMISSION_ID, 31L, "가온", StudentIdSubmissionStatus.PENDING, null, null, "https://s3/review",
                "홍길동", "트윈리대학교", SUBMITTED_AT, null,
                List.of(new AdminSchoolVerificationStudentIdDetailHistoryItemResult(
                        previousId, StudentIdSubmissionStatus.REJECTED, StudentIdRejectReason.BLURRY, "ops@trytwinly.com",
                        Instant.parse("2026-10-22T01:00:00Z"), Instant.parse("2026-10-22T02:00:00Z")))));

        // when: 상세 조회
        var result = mockMvc.perform(get("/admin/school-verifications/student-ids/{submissionId}", SUBMISSION_ID)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 200 + 사진·성명·이력, 판정 전이라 결과 필드는 null
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://s3/review"))
                .andExpect(jsonPath("$.identityName").value("홍길동"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectReason").isEmpty())
                .andExpect(jsonPath("$.history[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.history[0].rejectReason").value("BLURRY"));
    }

    @Test
    @DisplayName("검수 상세 조회 시 제출 ID 가 UUID 형식이 아니면 400을 반환한다")
    void studentIdDetail_non_uuid_returns_400() throws Exception {
        // when: UUID 가 아닌 경로 변수로 상세 조회
        var result = mockMvc.perform(get("/admin/school-verifications/student-ids/{submissionId}", "not-a-uuid")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 400 + 서비스 미호출
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(adminSchoolVerificationService);
    }

    @Test
    @DisplayName("판정 성공 시 200을 반환하고 검수자 앞뒤 공백을 지운 커맨드로 서비스를 호출한다")
    void decideStudentId_success() throws Exception {
        // when: 반려 사유와 함께 반려 판정
        var result = mockMvc.perform(post("/admin/school-verifications/student-ids/{submissionId}/decision", SUBMISSION_ID)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision":"REJECTED","rejectReason":"RRN_VISIBLE","reviewer":" ops@trytwinly.com "}
                        """));

        // then: 200 + 제출 ID·커맨드로 위임
        result.andExpect(status().isOk());
        then(adminSchoolVerificationService).should().decideStudentId(SUBMISSION_ID,
                new AdminSchoolVerificationDecideStudentIdCommand(StudentIdDecision.REJECTED, StudentIdRejectReason.RRN_VISIBLE, "ops@trytwinly.com"));
    }

    @Test
    @DisplayName("판정 요청에 검수자가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void decideStudentId_without_reviewer_returns_400() throws Exception {
        // when: reviewer 를 뺀 판정 요청
        var result = mockMvc.perform(post("/admin/school-verifications/student-ids/{submissionId}/decision", SUBMISSION_ID)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision":"APPROVED","rejectReason":null}
                        """));

        // then: 400 + 서비스 미호출
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(adminSchoolVerificationService);
    }

    @Test
    @DisplayName("이미 처리된 제출을 판정하면 409 STUDENT_ID_REVIEW_CONFLICT 를 반환한다")
    void decideStudentId_conflict_returns_409() throws Exception {
        // given: 다른 운영자가 먼저 처리해 서비스가 거절
        willThrow(new BusinessException(ErrorCode.STUDENT_ID_REVIEW_CONFLICT))
                .given(adminSchoolVerificationService).decideStudentId(eq(SUBMISSION_ID), any());

        // when: 승인 판정
        var result = mockMvc.perform(post("/admin/school-verifications/student-ids/{submissionId}/decision", SUBMISSION_ID)
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision":"APPROVED","rejectReason":null,"reviewer":"ops@trytwinly.com"}
                        """));

        // then: 409 + 에러 코드
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ID_REVIEW_CONFLICT"));
    }
}

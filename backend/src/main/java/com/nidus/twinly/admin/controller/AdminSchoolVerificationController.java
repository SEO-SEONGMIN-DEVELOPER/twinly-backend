package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.dto.command.AdminSchoolVerificationDecideStudentIdCommand;
import com.nidus.twinly.admin.dto.request.AdminSchoolVerificationDecideStudentIdRequest;
import com.nidus.twinly.admin.dto.response.AdminPageResponse;
import com.nidus.twinly.admin.dto.response.AdminSchoolVerificationStudentIdDetailResponse;
import com.nidus.twinly.admin.dto.response.AdminSchoolVerificationStudentIdsItemResponse;
import com.nidus.twinly.admin.service.AdminSchoolVerificationService;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "어드민 콘솔 (관리자)")
@RestController
@RequiredArgsConstructor
public class AdminSchoolVerificationController {

    private final AdminSchoolVerificationService adminSchoolVerificationService;

    @Operation(summary = "학생증 제출 목록", description = "status 를 비우면 모든 상태를 준다. 가입 중 교체된 제출(REPLACED)은 상세의 이전 제출에만 나오고 목록에서는 빠진다. 대기 중 제출은 앞에 오래된 순으로, 나머지는 최근 제출 순으로 온다. 탈퇴 신청(복구 기간 포함)한 회원의 대기 중 제출은 빠진다. elapsedMinutes 는 대기 중이면 지금까지, 판정됐으면 판정까지 걸린 분이고 판정 시각이 없으면 null 이다.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @GetMapping("/admin/school-verifications/student-ids")
    public AdminPageResponse<AdminSchoolVerificationStudentIdsItemResponse> studentIds(@RequestParam(required = false) StudentIdSubmissionStatus status,
                                                                                       @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return AdminPageResponse.from(adminSchoolVerificationService.studentIds(status, page, size), AdminSchoolVerificationStudentIdsItemResponse::from);
    }

    @Operation(summary = "학생증 검수 상세")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @ApiResponse(responseCode = "404", description = "STUDENT_ID_SUBMISSION_NOT_FOUND, USER_NOT_FOUND")
    })
    @GetMapping("/admin/school-verifications/student-ids/{submissionId}")
    public AdminSchoolVerificationStudentIdDetailResponse studentIdDetail(@PathVariable UUID submissionId) {
        return AdminSchoolVerificationStudentIdDetailResponse.from(adminSchoolVerificationService.studentIdDetail(submissionId));
    }

    @Operation(summary = "학생증 판정", description = "아직 대기 중이고 회원이 탈퇴 신청하지 않았을 때만 한 번 적용한다. 사진은 매시간 파기 작업이 지운다.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @ApiResponse(responseCode = "404", description = "STUDENT_ID_SUBMISSION_NOT_FOUND, USER_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "STUDENT_ID_REVIEW_CONFLICT")
    })
    @PostMapping("/admin/school-verifications/student-ids/{submissionId}/decision")
    public void decideStudentId(@PathVariable UUID submissionId,
                                @Valid @RequestBody AdminSchoolVerificationDecideStudentIdRequest request) {
        adminSchoolVerificationService.decideStudentId(submissionId, AdminSchoolVerificationDecideStudentIdCommand.from(request));
    }
}

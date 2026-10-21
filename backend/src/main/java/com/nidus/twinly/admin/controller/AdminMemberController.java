package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.domain.MemberSearchKey;
import com.nidus.twinly.admin.domain.MemberSort;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.command.AdminMemberSearchCommand;
import com.nidus.twinly.admin.dto.response.AdminMemberDetailResponse;
import com.nidus.twinly.admin.dto.response.AdminMemberListItemResponse;
import com.nidus.twinly.admin.dto.response.AdminPageResponse;
import com.nidus.twinly.admin.dto.response.AdminSceneResponse;
import com.nidus.twinly.admin.service.AdminMemberService;
import com.nidus.twinly.common.web.RequestId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "어드민 콘솔 (관리자)")
@Validated
@RestController
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @Operation(summary = "회원 검색", description = "시드 회원은 빠진다. 이메일과 학교는 암호화되어 있어 값 전체가 일치해야 찾는다. sort 는 SIGNUP(최근 가입 순, 기본값) 또는 ID(ID 내림차순)다.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @GetMapping("/admin/members")
    public AdminPageResponse<AdminMemberListItemResponse> search(@RequestParam(required = false) MemberSearchKey key,
                                                                 @RequestParam(required = false) String query,
                                                                 @RequestParam(required = false) MemberStatus status,
                                                                 @RequestParam(defaultValue = "SIGNUP") MemberSort sort,
                                                                 @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return AdminPageResponse.from(
                adminMemberService.search(new AdminMemberSearchCommand(key, query, status, sort, page, size)),
                AdminMemberListItemResponse::from);
    }

    @Operation(summary = "회원 상세")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/admin/members/{userId}")
    public AdminMemberDetailResponse detail(@PathVariable String userId) {
        return AdminMemberDetailResponse.from(adminMemberService.detail(RequestId.toLong(userId, "userId")));
    }

    @Operation(summary = "회원의 날짜별 AI 활동 장면")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/admin/members/{userId}/scenes")
    public List<AdminSceneResponse> scenes(@PathVariable String userId,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return adminMemberService.scenes(RequestId.toLong(userId, "userId"), date).stream()
                .map(AdminSceneResponse::from)
                .toList();
    }

    @Operation(summary = "학교 이름 자동완성")
    @GetMapping("/admin/organizations")
    public List<String> organizations(@RequestParam(required = false) String query) {
        return adminMemberService.organizationNames(query);
    }
}

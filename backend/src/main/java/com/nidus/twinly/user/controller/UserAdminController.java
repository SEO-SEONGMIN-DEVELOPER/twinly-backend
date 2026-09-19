package com.nidus.twinly.user.controller;

import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.user.service.UserPersonaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "유저 (관리자)")
@RestController
@RequiredArgsConstructor
public class UserAdminController {

    private final UserPersonaService userPersonaService;

    @Operation(summary = "유저 페르소나 초기화", description = "설문 답, AI 대화, 관심사를 포함한 페르소나를 지우고 AI 대화 완료 시각을 비운다.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @DeleteMapping("/admin/users/{userId}/persona")
    public void resetPersona(@PathVariable String userId) {
        userPersonaService.resetPersona(RequestId.toLong(userId, "userId"));
    }
}

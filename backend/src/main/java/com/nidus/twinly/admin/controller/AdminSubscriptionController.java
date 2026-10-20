package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.domain.SubscriptionKind;
import com.nidus.twinly.admin.dto.response.AdminSubscriptionListResponse;
import com.nidus.twinly.admin.service.AdminSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어드민 콘솔 (관리자)")
@Validated
@RestController
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    @Operation(summary = "구독 목록", description = "시드 회원은 빠진다. 스토어·기간 유형은 다음 RevenueCat 동기화부터 채워진다.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @GetMapping("/admin/subscriptions")
    public AdminSubscriptionListResponse list(@RequestParam(required = false) SubscriptionKind kind,
                                              @RequestParam(defaultValue = "true") Boolean activeOnly,
                                              @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return AdminSubscriptionListResponse.from(adminSubscriptionService.list(kind, activeOnly, page, size));
    }
}

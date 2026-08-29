package com.nidus.twinly.activity.controller;

import com.nidus.twinly.activity.dto.response.ActivityResponse;
import com.nidus.twinly.activity.service.ActivityService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "활동")
@RestController
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "날짜별 활동 내역 조회")
    @GetMapping("/api/v1/activities/{date}")
    public ActivityResponse activity(@AuthenticationPrincipal UserInfo userInfo,
                                     @PathVariable LocalDate date) {
        return ActivityResponse.from(activityService.activity(userInfo.id(), date));
    }
}

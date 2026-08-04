package com.nidus.twinly.activity.controller;

import com.nidus.twinly.activity.dto.response.ActivityResponse;
import com.nidus.twinly.activity.service.ActivityService;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/api/v1/activities/{date}")
    public ActivityResponse activity(@AuthenticationPrincipal UserInfo userInfo,
                                     @PathVariable LocalDate date) {
        return ActivityResponse.from(activityService.activity(userInfo.id(), date));
    }
}

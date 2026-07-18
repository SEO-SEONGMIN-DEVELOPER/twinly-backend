package com.nidus.twinly.activity.controller;

import com.nidus.twinly.activity.dto.response.ActivityResponse;
import com.nidus.twinly.activity.service.ActivityService;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/api/v1/activity/{date}")
    public ActivityResponse activity(@CurrentUser UserInfo userInfo,
                                     @PathVariable LocalDate date) {
        return ActivityResponse.from(activityService.activity(userInfo.id(), date));
    }
}

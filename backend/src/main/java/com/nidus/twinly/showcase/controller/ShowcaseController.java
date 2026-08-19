package com.nidus.twinly.showcase.controller;

import com.nidus.twinly.showcase.dto.response.ShowcaseTodayResponse;
import com.nidus.twinly.showcase.service.ShowcaseService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShowcaseController {

    private final ShowcaseService showcaseService;

    @ApiResponse(responseCode = "404", description = "SHOWCASE_TARGET_NOT_FOUND, USER_NOT_FOUND")
    @GetMapping("/api/v1/showcases/today")
    public ShowcaseTodayResponse today(@AuthenticationPrincipal UserInfo userInfo) {
        return ShowcaseTodayResponse.from(showcaseService.today(userInfo.id()));
    }
}

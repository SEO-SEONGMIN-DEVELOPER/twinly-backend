package com.nidus.twinly.main.controller;

import com.nidus.twinly.main.dto.response.MainTabResponse;
import com.nidus.twinly.main.service.MainService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "메인")
@RestController
@RequiredArgsConstructor
public class MainController {

    private final MainService mainService;

    @GetMapping("/api/v1/main")
    public MainTabResponse mainTab(@AuthenticationPrincipal UserInfo userInfo) {
        return MainTabResponse.from(mainService.mainTab(userInfo.id()));
    }
}

package com.nidus.twinly.onboarding.controller;

import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.onboarding.dto.OnboardingBasicInfoCommand;
import com.nidus.twinly.onboarding.dto.OnboardingBasicInfoRequest;
import com.nidus.twinly.onboarding.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/api/v1/onboarding/basic-info")
    public void basicInfo(@CurrentAnonSession AnonSession anonSession,
                          @RequestBody OnboardingBasicInfoRequest request) throws Exception {
        onboardingService.basicInfo(anonSession, OnboardingBasicInfoCommand.from(request));
    }
}
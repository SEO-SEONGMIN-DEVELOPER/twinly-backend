package com.nidus.twinly.onboarding.controller;

import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.AnonSessionInfo;
import com.nidus.twinly.onboarding.dto.command.OnboardingBasicInfoCommand;
import com.nidus.twinly.onboarding.dto.command.OnboardingSurveyAnswerCommand;
import com.nidus.twinly.onboarding.dto.request.OnboardingBasicInfoRequest;
import com.nidus.twinly.onboarding.dto.request.OnboardingSurveyAnswerRequest;
import com.nidus.twinly.onboarding.dto.response.OnboardingSurveyAnswerResponse;
import com.nidus.twinly.onboarding.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/api/v1/onboarding/basic-info")
    public void basicInfo(@CurrentAnonSession AnonSessionInfo anonSessionInfo,
                          @RequestBody OnboardingBasicInfoRequest request) {
        onboardingService.basicInfo(anonSessionInfo.id(), OnboardingBasicInfoCommand.from(request));
    }

    @GetMapping("/api/v1/onboarding/survey-questions")
    public ResponseEntity<String> surveyQuestions() throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(onboardingService.surveyQuestions());
    }

    @PostMapping("/api/v1/onboarding/survey-answer")
    public OnboardingSurveyAnswerResponse surveyAnswer (@CurrentAnonSession AnonSessionInfo anonSessionInfo,
                                                        @RequestBody OnboardingSurveyAnswerRequest request) {
        return OnboardingSurveyAnswerResponse.from(onboardingService.surveyAnswer(anonSessionInfo.id(), OnboardingSurveyAnswerCommand.from(request)));
    }
}
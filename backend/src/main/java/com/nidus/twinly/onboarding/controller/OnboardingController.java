package com.nidus.twinly.onboarding.controller;

import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.header.AnonSessionInfo;
import com.nidus.twinly.onboarding.dto.command.*;
import com.nidus.twinly.onboarding.dto.request.*;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfilePhotoCommitResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfilePhotoPresignResponse;
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
    public void surveyAnswer(@CurrentAnonSession AnonSessionInfo anonSessionInfo,
                              @RequestBody OnboardingSurveyAnswerRequest request) {
        onboardingService.surveyAnswer(anonSessionInfo.id(), OnboardingSurveyAnswerCommand.from(request));
    }

    @PostMapping("/api/v1/onboarding/interests")
    public void interests(@CurrentAnonSession AnonSessionInfo anonSessionInfo,
                           @RequestBody OnboardingInterestsRequest request) {
        onboardingService.interests(anonSessionInfo.id(), OnboardingInterestsCommand.from(request));
    }

    @PostMapping("/api/v1/onboarding/profile/photo/presign")
    public OnboardingProfilePhotoPresignResponse profilePhotoPresign(@CurrentAnonSession AnonSessionInfo anonSessionInfo,
                                                                     @RequestBody OnboardingProfilePhotoPresignRequest request) {
        return OnboardingProfilePhotoPresignResponse.from(onboardingService.profilePhotoPresign(anonSessionInfo.id(), OnboardingProfilePhotoPresignCommand.from(request)));
    }

    @PostMapping("/api/v1/onboarding/profile/photo/commit")
    public OnboardingProfilePhotoCommitResponse profilePhotoCommit(@CurrentAnonSession AnonSessionInfo anonSessionInfo,
                                                                   @RequestBody OnboardingProfilePhotoCommitRequest request) {
        return OnboardingProfilePhotoCommitResponse.from(onboardingService.profilePhotoCommit(anonSessionInfo.id(), OnboardingProfilePhotoCommitCommand.from(request)));
    }
}
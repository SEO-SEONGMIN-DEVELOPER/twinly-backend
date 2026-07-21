package com.nidus.twinly.onboarding.controller;

import com.nidus.twinly.aichat.service.AiChatService;
import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.onboarding.dto.command.*;
import com.nidus.twinly.onboarding.dto.request.*;
import com.nidus.twinly.onboarding.dto.response.OnboardingAiChatMessageResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingAiChatStartResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfileNicknameCheckResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfilePhotoCommitResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfilePhotoPresignResponse;
import com.nidus.twinly.onboarding.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final AiChatService aiChatService;

    @PostMapping("/api/v1/onboarding/basic-info")
    public void basicInfo(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                          @Valid @RequestBody OnboardingBasicInfoRequest request) {
        onboardingService.basicInfo(anonSessionSnapshot, OnboardingBasicInfoCommand.from(request));
    }

    @GetMapping("/api/v1/onboarding/survey-questions")
    public ResponseEntity<String> surveyQuestions() throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(onboardingService.surveyQuestions());
    }

    @PostMapping("/api/v1/onboarding/survey-answer")
    public void surveyAnswer(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                              @Valid @RequestBody OnboardingSurveyAnswerRequest request) {
        onboardingService.surveyAnswer(anonSessionSnapshot, OnboardingSurveyAnswerCommand.from(request));
    }

    @PostMapping("/api/v1/onboarding/interests")
    public void interests(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                           @Valid @RequestBody OnboardingInterestsRequest request) {
        onboardingService.interests(anonSessionSnapshot, OnboardingInterestsCommand.from(request));
    }

    @PostMapping("/api/v1/onboarding/profile/photo/presign")
    public OnboardingProfilePhotoPresignResponse profilePhotoPresign(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                                     @Valid @RequestBody OnboardingProfilePhotoPresignRequest request) {
        return OnboardingProfilePhotoPresignResponse.from(onboardingService.profilePhotoPresign(anonSessionSnapshot, OnboardingProfilePhotoPresignCommand.from(request)));
    }

    @PostMapping("/api/v1/onboarding/profile/photo/commit")
    public OnboardingProfilePhotoCommitResponse profilePhotoCommit(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                                   @Valid @RequestBody OnboardingProfilePhotoCommitRequest request) {
        return OnboardingProfilePhotoCommitResponse.from(onboardingService.profilePhotoCommit(anonSessionSnapshot, OnboardingProfilePhotoCommitCommand.from(request)));
    }

    @PostMapping("/api/v1/onboarding/profile/nickname/check")
    public OnboardingProfileNicknameCheckResponse profileNicknameCheck(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                                       @Valid @RequestBody OnboardingProfileNicknameCheckRequest request) {
        return OnboardingProfileNicknameCheckResponse.from(onboardingService.profileNicknameCheck(anonSessionSnapshot, OnboardingProfileNicknameCheckCommand.from(request)));
    }

    @PostMapping("/api/v1/onboarding/profile/nickname")
    public void profileNickname(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                 @Valid @RequestBody OnboardingProfileNicknameRequest request) {
        onboardingService.profileNickname(anonSessionSnapshot, OnboardingProfileNicknameCommand.from(request));
    }

    @PostMapping("/api/v1/onboarding/ai-chat/start")
    public OnboardingAiChatStartResponse aiChatStart(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot) {
        return OnboardingAiChatStartResponse.from(aiChatService.aiChatStart(anonSessionSnapshot));
    }

    @PostMapping("/api/v1/onboarding/ai-chat/message")
    public OnboardingAiChatMessageResponse aiChatMessage(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                         @Valid @RequestBody OnboardingAiChatMessageRequest request) {
        return OnboardingAiChatMessageResponse.from(aiChatService.aiChatMessage(anonSessionSnapshot, OnboardingAiChatMessageCommand.from(request)));
    }

    @PostMapping("/api/v1/onboarding/consents")
    public void grantConsents(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                              @Valid @RequestBody OnboardingGrantConsentsRequest request) {
        onboardingService.grantConsents(anonSessionSnapshot, OnboardingGrantConsentsCommand.from(request));
    }

    @DeleteMapping("/api/v1/onboarding/consents")
    public void revokeConsents(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                               @Valid @RequestBody OnboardingRevokeConsentsRequest request) {
        onboardingService.revokeConsents(anonSessionSnapshot, OnboardingRevokeConsentsCommand.from(request));
    }
}

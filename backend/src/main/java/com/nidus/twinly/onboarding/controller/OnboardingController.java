package com.nidus.twinly.onboarding.controller;

import com.nidus.twinly.aichat.service.AiChatService;
import com.nidus.twinly.anon.annotation.CurrentAnonSession;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.onboarding.dto.command.*;
import com.nidus.twinly.onboarding.dto.request.*;
import com.nidus.twinly.onboarding.dto.response.OnboardingAffiliationsResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingAiChatMessageResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingAiChatStartResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfileNicknameCheckResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfilePhotoCommitResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfilePhotoPresignResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingSchoolsResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingSurveyQuestionResponse;
import com.nidus.twinly.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final AiChatService aiChatService;

    @PutMapping("/api/v1/onboarding/basic-info")
    public void basicInfo(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                          @Valid @RequestBody OnboardingBasicInfoRequest request) {
        onboardingService.basicInfo(anonSessionSnapshot, OnboardingBasicInfoCommand.from(request));
    }

    @GetMapping("/api/v1/onboarding/survey-questions")
    public List<OnboardingSurveyQuestionResponse> surveyQuestions() {
        return onboardingService.surveyQuestions().stream()
                .map(OnboardingSurveyQuestionResponse::from)
                .toList();
    }

    @ApiResponse(responseCode = "404", description = "SURVEY_QUESTION_NOT_FOUND")
    @PostMapping("/api/v1/onboarding/survey-answers")
    public void surveyAnswer(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                              @Valid @RequestBody OnboardingSurveyAnswerRequest request) {
        onboardingService.surveyAnswer(anonSessionSnapshot, OnboardingSurveyAnswerCommand.from(request));
    }

    @PostMapping("/api/v1/onboarding/interests")
    public void interests(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                           @Valid @RequestBody OnboardingInterestsRequest request) {
        onboardingService.interests(anonSessionSnapshot, OnboardingInterestsCommand.from(request));
    }

    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_TYPE")
    @PostMapping("/api/v1/onboarding/profile/photo/presign")
    public OnboardingProfilePhotoPresignResponse profilePhotoPresign(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                                     @Valid @RequestBody OnboardingProfilePhotoPresignRequest request) {
        return OnboardingProfilePhotoPresignResponse.from(onboardingService.profilePhotoPresign(anonSessionSnapshot, OnboardingProfilePhotoPresignCommand.from(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_KEY_OWNER"),
            @ApiResponse(responseCode = "422", description = "UPLOAD_NOT_COMPLETED")
    })
    @PostMapping("/api/v1/onboarding/profile/photo/commit")
    public OnboardingProfilePhotoCommitResponse profilePhotoCommit(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                                   @Valid @RequestBody OnboardingProfilePhotoCommitRequest request) {
        return OnboardingProfilePhotoCommitResponse.from(onboardingService.profilePhotoCommit(anonSessionSnapshot, OnboardingProfilePhotoCommitCommand.from(request)));
    }

    @ApiResponse(responseCode = "422", description = "INVALID_NICKNAME")
    @PostMapping("/api/v1/onboarding/profile/nickname/check")
    public OnboardingProfileNicknameCheckResponse profileNicknameCheck(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                                       @Valid @RequestBody OnboardingProfileNicknameCheckRequest request) {
        return OnboardingProfileNicknameCheckResponse.from(onboardingService.profileNicknameCheck(anonSessionSnapshot, OnboardingProfileNicknameCheckCommand.from(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "NICKNAME_ALREADY_USED"),
            @ApiResponse(responseCode = "422", description = "INVALID_NICKNAME")
    })
    @PutMapping("/api/v1/onboarding/profile/nickname")
    public void profileNickname(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                 @Valid @RequestBody OnboardingProfileNicknameRequest request) {
        onboardingService.profileNickname(anonSessionSnapshot, OnboardingProfileNicknameCommand.from(request));
    }

    @PostMapping("/api/v1/onboarding/ai-chat/start")
    public OnboardingAiChatStartResponse aiChatStart(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot) {
        return OnboardingAiChatStartResponse.from(aiChatService.aiChatStart(anonSessionSnapshot));
    }

    @ApiResponse(responseCode = "404", description = "AI_QUESTION_NOT_FOUND")
    @PostMapping("/api/v1/onboarding/ai-chat/messages")
    public OnboardingAiChatMessageResponse aiChatMessage(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                                                         @Valid @RequestBody OnboardingAiChatMessageRequest request) {
        return OnboardingAiChatMessageResponse.from(aiChatService.aiChatMessage(anonSessionSnapshot, OnboardingAiChatMessageCommand.from(request)));
    }

    @ApiResponse(responseCode = "404", description = "POLICY_NOT_FOUND")
    @PostMapping("/api/v1/onboarding/consents")
    public void grantConsents(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                              @Valid @RequestBody OnboardingGrantConsentsRequest request) {
        onboardingService.grantConsents(anonSessionSnapshot, OnboardingGrantConsentsCommand.from(request));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "REQUIRED_POLICY_REVOKE_DENIED"),
            @ApiResponse(responseCode = "404", description = "POLICY_NOT_FOUND")
    })
    @PostMapping("/api/v1/onboarding/consents/revoke")
    public void revokeConsents(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                               @Valid @RequestBody OnboardingRevokeConsentsRequest request) {
        onboardingService.revokeConsents(anonSessionSnapshot, OnboardingRevokeConsentsCommand.from(request));
    }

    @GetMapping("/api/v1/onboarding/schools")
    public OnboardingSchoolsResponse schools() {
        return OnboardingSchoolsResponse.from(onboardingService.schools());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "422", description = "EMAIL_VERIFICATION_NOT_COMPLETED, EMAIL_DOMAIN_NOT_SUPPORTED")
    })
    @GetMapping("/api/v1/onboarding/affiliations")
    public OnboardingAffiliationsResponse affiliations(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot) {
        return OnboardingAffiliationsResponse.from(onboardingService.affiliations(anonSessionSnapshot));
    }

    @PostMapping("/api/v1/onboarding/affiliation")
    public void affiliation(@CurrentAnonSession AnonSessionSnapshot anonSessionSnapshot,
                            @Valid @RequestBody OnboardingAffiliationRequest request) {
        onboardingService.affiliation(anonSessionSnapshot, OnboardingAffiliationCommand.from(request));
    }
}

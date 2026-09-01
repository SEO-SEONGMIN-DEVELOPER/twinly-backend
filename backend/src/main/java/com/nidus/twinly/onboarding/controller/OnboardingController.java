package com.nidus.twinly.onboarding.controller;

import com.nidus.twinly.aichat.service.AiChatService;
import com.nidus.twinly.anon.dto.snapshot.AnonSessionSnapshot;
import com.nidus.twinly.onboarding.dto.command.*;
import com.nidus.twinly.onboarding.dto.request.*;
import com.nidus.twinly.onboarding.dto.response.OnboardingAffiliationsResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingAiChatMessageResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingAiChatStartResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingOrganizationsResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfileNicknameCheckResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfilePhotoCommitResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingProfilePhotoPresignResponse;
import com.nidus.twinly.onboarding.dto.response.OnboardingSurveyQuestionResponse;
import com.nidus.twinly.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "온보딩")
@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final AiChatService aiChatService;

    @Operation(summary = "기본 정보 입력")
    @PutMapping("/api/v1/onboarding/basic-info")
    public void basicInfo(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                          @Valid @RequestBody OnboardingBasicInfoRequest request) {
        onboardingService.basicInfo(anonSessionSnapshot, OnboardingBasicInfoCommand.from(request));
    }

    @Operation(summary = "설문 문항 목록 조회")
    @GetMapping("/api/v1/onboarding/survey-questions")
    public List<OnboardingSurveyQuestionResponse> surveyQuestions() {
        return onboardingService.surveyQuestions().stream()
                .map(OnboardingSurveyQuestionResponse::from)
                .toList();
    }

    @Operation(summary = "설문 응답 제출")
    @ApiResponse(responseCode = "404", description = "SURVEY_QUESTION_NOT_FOUND")
    @PostMapping("/api/v1/onboarding/survey-answers")
    public void surveyAnswer(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                              @Valid @RequestBody OnboardingSurveyAnswerRequest request) {
        onboardingService.surveyAnswer(anonSessionSnapshot, OnboardingSurveyAnswerCommand.from(request));
    }

    @Operation(summary = "관심사 선택")
    @PostMapping("/api/v1/onboarding/interests")
    public void interests(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                           @Valid @RequestBody OnboardingInterestsRequest request) {
        onboardingService.interests(anonSessionSnapshot, OnboardingInterestsCommand.from(request));
    }

    @Operation(summary = "프로필 사진 업로드 URL 발급")
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_TYPE")
    @PostMapping("/api/v1/onboarding/profile/photo/presign")
    public OnboardingProfilePhotoPresignResponse profilePhotoPresign(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                                                     @Valid @RequestBody OnboardingProfilePhotoPresignRequest request) {
        return OnboardingProfilePhotoPresignResponse.from(onboardingService.profilePhotoPresign(anonSessionSnapshot, OnboardingProfilePhotoPresignCommand.from(request)));
    }

    @Operation(summary = "프로필 사진 업로드 확정")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_KEY_OWNER"),
            @ApiResponse(responseCode = "422", description = "UPLOAD_NOT_COMPLETED")
    })
    @PostMapping("/api/v1/onboarding/profile/photo/commit")
    public OnboardingProfilePhotoCommitResponse profilePhotoCommit(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                                                   @Valid @RequestBody OnboardingProfilePhotoCommitRequest request) {
        return OnboardingProfilePhotoCommitResponse.from(onboardingService.profilePhotoCommit(anonSessionSnapshot, OnboardingProfilePhotoCommitCommand.from(request)));
    }

    @Operation(summary = "닉네임 중복 확인")
    @ApiResponse(responseCode = "422", description = "INVALID_NICKNAME")
    @PostMapping("/api/v1/onboarding/profile/nickname/check")
    public OnboardingProfileNicknameCheckResponse profileNicknameCheck(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                                                       @Valid @RequestBody OnboardingProfileNicknameCheckRequest request) {
        return OnboardingProfileNicknameCheckResponse.from(onboardingService.profileNicknameCheck(anonSessionSnapshot, OnboardingProfileNicknameCheckCommand.from(request)));
    }

    @Operation(summary = "닉네임 설정")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "NICKNAME_ALREADY_USED"),
            @ApiResponse(responseCode = "422", description = "INVALID_NICKNAME")
    })
    @PutMapping("/api/v1/onboarding/profile/nickname")
    public void profileNickname(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                 @Valid @RequestBody OnboardingProfileNicknameRequest request) {
        onboardingService.profileNickname(anonSessionSnapshot, OnboardingProfileNicknameCommand.from(request));
    }

    @Operation(summary = "온보딩 AI 대화 시작")
    @PostMapping("/api/v1/onboarding/ai-chat/start")
    public OnboardingAiChatStartResponse aiChatStart(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot) {
        return OnboardingAiChatStartResponse.from(aiChatService.aiChatStart(anonSessionSnapshot));
    }

    @Operation(summary = "온보딩 AI 대화 메시지 전송")
    @ApiResponse(responseCode = "404", description = "AI_QUESTION_NOT_FOUND")
    @PostMapping("/api/v1/onboarding/ai-chat/messages")
    public OnboardingAiChatMessageResponse aiChatMessage(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                                                         @Valid @RequestBody OnboardingAiChatMessageRequest request) {
        return OnboardingAiChatMessageResponse.from(aiChatService.aiChatMessage(anonSessionSnapshot, OnboardingAiChatMessageCommand.from(request)));
    }

    @Operation(summary = "약관 동의")
    @ApiResponse(responseCode = "404", description = "POLICY_NOT_FOUND")
    @PostMapping("/api/v1/onboarding/consents")
    public void grantConsents(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                              @Valid @RequestBody OnboardingGrantConsentsRequest request) {
        onboardingService.grantConsents(anonSessionSnapshot, OnboardingGrantConsentsCommand.from(request));
    }

    @Operation(summary = "약관 동의 철회")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "REQUIRED_POLICY_REVOKE_DENIED"),
            @ApiResponse(responseCode = "404", description = "POLICY_NOT_FOUND")
    })
    @PostMapping("/api/v1/onboarding/consents/revoke")
    public void revokeConsents(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                               @Valid @RequestBody OnboardingRevokeConsentsRequest request) {
        onboardingService.revokeConsents(anonSessionSnapshot, OnboardingRevokeConsentsCommand.from(request));
    }

    @Operation(summary = "기관 목록 조회")
    @GetMapping("/api/v1/onboarding/organizations")
    public OnboardingOrganizationsResponse organizations() {
        return OnboardingOrganizationsResponse.from(onboardingService.organizations());
    }

    @Operation(summary = "부서 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "422", description = "EMAIL_VERIFICATION_NOT_COMPLETED, EMAIL_DOMAIN_NOT_SUPPORTED")
    })
    @GetMapping("/api/v1/onboarding/affiliations")
    public OnboardingAffiliationsResponse affiliations(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot) {
        return OnboardingAffiliationsResponse.from(onboardingService.affiliations(anonSessionSnapshot));
    }

    @Operation(summary = "부서 등록")
    @PostMapping("/api/v1/onboarding/affiliation")
    public void affiliation(@AuthenticationPrincipal AnonSessionSnapshot anonSessionSnapshot,
                            @Valid @RequestBody OnboardingAffiliationRequest request) {
        onboardingService.affiliation(anonSessionSnapshot, OnboardingAffiliationCommand.from(request));
    }
}

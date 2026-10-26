package com.nidus.twinly.me.controller;

import com.nidus.twinly.aichat.service.UserAiChatService;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersion;
import com.nidus.twinly.app.filter.AppBlockFilter;
import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.me.domain.HesitationDuration;
import com.nidus.twinly.me.domain.HesitationStatus;
import com.nidus.twinly.me.dto.command.MeAiChatMessageCommand;
import com.nidus.twinly.me.dto.command.MeSubmitTwinUpdateSurveyAnswerCommand;
import com.nidus.twinly.me.dto.command.MeStartTwinUpdateCommand;
import com.nidus.twinly.me.dto.command.MeStartTwinUpdateAiChatCommand;
import com.nidus.twinly.me.dto.command.MeSendTwinUpdateAiChatMessageCommand;
import com.nidus.twinly.me.dto.command.MeSaveTwinUpdateInterestsCommand;
import com.nidus.twinly.me.dto.command.MeCompleteTwinUpdateCommand;
import com.nidus.twinly.me.dto.command.MeAppNotificationsReadAllCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileNicknameCommand;
import com.nidus.twinly.me.dto.command.MeCheckProfileNicknameCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileVisibilitySettingCommand;
import com.nidus.twinly.me.dto.command.MeChangePushNotificationsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsCommand;
import com.nidus.twinly.me.dto.command.MeHesitationsAnswerCommand;
import com.nidus.twinly.me.dto.command.MeInterestsCommand;
import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsCommand;
import com.nidus.twinly.me.dto.command.MeSchoolVerificationStudentIdPresignCommand;
import com.nidus.twinly.me.dto.command.MeSchoolVerificationStudentIdSubmitCommand;
import com.nidus.twinly.me.dto.command.MeSendFeedbackCommand;
import com.nidus.twinly.me.dto.command.MeSubmitTendencyAnswerCommand;
import com.nidus.twinly.me.dto.command.MeSurveyAnswerCommand;
import com.nidus.twinly.me.dto.request.MeAiChatMessageRequest;
import com.nidus.twinly.me.dto.response.MeTwinUpdateResponse;
import com.nidus.twinly.me.dto.response.MeCompleteTwinUpdateResponse;
import com.nidus.twinly.me.dto.request.MeSubmitTwinUpdateSurveyAnswerRequest;
import com.nidus.twinly.me.dto.request.MeStartTwinUpdateRequest;
import com.nidus.twinly.me.dto.request.MeStartTwinUpdateAiChatRequest;
import com.nidus.twinly.me.dto.request.MeSendTwinUpdateAiChatMessageRequest;
import com.nidus.twinly.me.dto.request.MeSaveTwinUpdateInterestsRequest;
import com.nidus.twinly.me.dto.request.MeCompleteTwinUpdateRequest;
import com.nidus.twinly.me.dto.request.MeAppNotificationsReadAllRequest;
import com.nidus.twinly.me.dto.request.MeChangeProfileNicknameRequest;
import com.nidus.twinly.me.dto.request.MeCheckProfileNicknameRequest;
import com.nidus.twinly.me.dto.request.MeChangeProfileVisibilitySettingRequest;
import com.nidus.twinly.me.dto.request.MeChangePushNotificationsRequest;
import com.nidus.twinly.me.dto.request.MeGrantConsentsRequest;
import com.nidus.twinly.me.dto.request.MeHesitationsAnswerRequest;
import com.nidus.twinly.me.dto.request.MeInterestsRequest;
import com.nidus.twinly.me.dto.request.MeProfileRequest;
import com.nidus.twinly.me.dto.request.MeProfilePhotoCommitRequest;
import com.nidus.twinly.me.dto.request.MeProfilePhotoPresignRequest;
import com.nidus.twinly.me.dto.request.MeRevokeConsentsRequest;
import com.nidus.twinly.me.dto.request.MeSchoolVerificationStudentIdPresignRequest;
import com.nidus.twinly.me.dto.request.MeSchoolVerificationStudentIdSubmitRequest;
import com.nidus.twinly.me.dto.request.MeSendFeedbackRequest;
import com.nidus.twinly.me.dto.request.MeSubmitTendencyAnswerRequest;
import com.nidus.twinly.me.dto.request.MeSurveyAnswerRequest;
import com.nidus.twinly.me.dto.response.MeAiChatMessageResponse;
import com.nidus.twinly.me.dto.response.MeAiChatStartResponse;
import com.nidus.twinly.me.dto.response.MeAppNotificationsFeedsResponse;
import com.nidus.twinly.me.dto.response.MeAppNotificationsUnreadCountResponse;
import com.nidus.twinly.me.dto.response.MeCheckProfileNicknameResponse;
import com.nidus.twinly.me.dto.response.MeConsentsResponse;
import com.nidus.twinly.me.dto.response.MeFeedbackOptionsResponse;
import com.nidus.twinly.me.dto.response.MeHesitationsResponse;
import com.nidus.twinly.me.dto.response.MePersonalityTypeResponse;
import com.nidus.twinly.me.dto.response.MePushNotificationsResponse;
import com.nidus.twinly.me.dto.response.MeProfileEditViewResponse;
import com.nidus.twinly.me.dto.response.MeProfileResponse;
import com.nidus.twinly.me.dto.response.MeProfileV2Response;
import com.nidus.twinly.me.dto.response.MePurchasesResponse;
import com.nidus.twinly.me.dto.response.MeInfoResponse;
import com.nidus.twinly.me.dto.response.MeProfilePhotoCommitResponse;
import com.nidus.twinly.me.dto.response.MeProfilePhotoPresignResponse;
import com.nidus.twinly.me.dto.response.MeProfileVisibilitySettingsResponse;
import com.nidus.twinly.me.dto.response.MeSchoolVerificationStudentIdPresignResponse;
import com.nidus.twinly.me.dto.response.MeStatusResponse;
import com.nidus.twinly.me.dto.response.MeSurveyQuestionResponse;
import com.nidus.twinly.me.dto.response.MeTendencyQuestionsResponse;
import com.nidus.twinly.me.dto.response.MeWithdrawResponse;
import com.nidus.twinly.me.service.MeService;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "내 정보")
@RestController
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;
    private final UserAiChatService userAiChatService;

    @Operation(summary = "프로필 사진 업로드 URL 발급")
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_TYPE")
    @PostMapping("/api/v1/me/profile/photo/presign")
    public MeProfilePhotoPresignResponse profilePhotoPresign(@AuthenticationPrincipal UserInfo userInfo,
                                                              @Valid @RequestBody MeProfilePhotoPresignRequest request) {
        return MeProfilePhotoPresignResponse.from(meService.profilePhotoPresign(userInfo.id(), MeProfilePhotoPresignCommand.from(request)));
    }

    @Operation(summary = "프로필 사진 업로드 확정")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_KEY_OWNER"),
            @ApiResponse(responseCode = "422", description = "UPLOAD_NOT_COMPLETED")
    })
    @PostMapping("/api/v1/me/profile/photo/commit")
    public MeProfilePhotoCommitResponse profilePhotoCommit(@AuthenticationPrincipal UserInfo userInfo,
                                                            @Valid @RequestBody MeProfilePhotoCommitRequest request) {
        return MeProfilePhotoCommitResponse.from(meService.profilePhotoCommit(userInfo.id(), MeProfilePhotoCommitCommand.from(request)));
    }

    @Operation(summary = "회원 탈퇴 신청")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @DeleteMapping("/api/v1/me")
    public MeWithdrawResponse withdraw(@AuthenticationPrincipal UserInfo userInfo) {
        return MeWithdrawResponse.from(meService.withdraw(userInfo.id()));
    }

    @Operation(summary = "프로필 수정 화면 조회")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/me/profile-edit-view")
    public MeProfileEditViewResponse profileEditView(@AuthenticationPrincipal UserInfo userInfo) {
        return MeProfileEditViewResponse.from(meService.profileEditView(userInfo.id()));
    }

    @Operation(summary = "내 프로필 수정")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @PatchMapping("/api/v1/me/profile")
    public void profile(@AuthenticationPrincipal UserInfo userInfo,
                        @Valid @RequestBody MeProfileRequest request) {
        meService.profile(userInfo.id(), MeProfileCommand.from(request));
    }

    @Operation(summary = "설문 문항 목록 조회")
    @GetMapping("/api/v1/me/survey-questions")
    public List<MeSurveyQuestionResponse> surveyQuestions() {
        return meService.surveyQuestions().stream()
                .map(MeSurveyQuestionResponse::from)
                .toList();
    }

    @Operation(summary = "설문 응답 제출")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "SURVEY_QUESTION_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "SURVEY_ANSWERS_INCOMPLETE")
    })
    @PostMapping("/api/v1/me/survey-answers")
    public void surveyAnswer(@AuthenticationPrincipal UserInfo userInfo,
                             @Valid @RequestBody MeSurveyAnswerRequest request) {
        meService.surveyAnswer(userInfo.id(), MeSurveyAnswerCommand.from(request));
    }

    @Operation(summary = "관심사 선택")
    @PostMapping("/api/v1/me/interests")
    public void interests(@AuthenticationPrincipal UserInfo userInfo,
                          @Valid @RequestBody MeInterestsRequest request) {
        meService.interests(userInfo.id(), MeInterestsCommand.from(request));
    }

    @Operation(summary = "AI 대화 시작")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @PostMapping("/api/v1/me/ai-chat/start")
    public MeAiChatStartResponse aiChatStart(@AuthenticationPrincipal UserInfo userInfo) {
        return MeAiChatStartResponse.from(userAiChatService.aiChatStart(userInfo.id()));
    }

    @Operation(summary = "AI 대화 메시지 전송")
    @ApiResponse(responseCode = "404", description = "AI_QUESTION_NOT_FOUND, USER_NOT_FOUND")
    @PostMapping("/api/v1/me/ai-chat/messages")
    public MeAiChatMessageResponse aiChatMessage(@AuthenticationPrincipal UserInfo userInfo,
                                                 @Valid @RequestBody MeAiChatMessageRequest request) {
        return MeAiChatMessageResponse.from(userAiChatService.aiChatMessage(userInfo.id(), MeAiChatMessageCommand.from(request)));
    }

    @Operation(summary = "AI 대화 종료")
    @PostMapping("/api/v1/me/ai-chat/complete")
    public void aiChatComplete(@AuthenticationPrincipal UserInfo userInfo) {
        userAiChatService.aiChatComplete(userInfo.id());
    }

    @Operation(summary = "탈퇴 철회")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "WITHDRAWAL_RECOVERY_EXPIRED")
    })
    @PostMapping("/api/v1/me/restore")
    public void restore(@AuthenticationPrincipal UserInfo userInfo) {
        meService.restore(userInfo.id());
    }

    @Operation(summary = "약관 동의 현황 조회")
    @GetMapping("/api/v1/me/consents")
    public MeConsentsResponse consents(@AuthenticationPrincipal UserInfo userInfo) {
        return MeConsentsResponse.from(meService.consents(userInfo.id()));
    }

    @Operation(summary = "약관 동의")
    @ApiResponse(responseCode = "404", description = "POLICY_NOT_FOUND")
    @PostMapping("/api/v1/me/consents")
    public void grantConsents(@AuthenticationPrincipal UserInfo userInfo,
                              @Valid @RequestBody MeGrantConsentsRequest request) {
        meService.grantConsents(userInfo.id(), MeGrantConsentsCommand.from(request));
    }

    @Operation(summary = "약관 동의 철회")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "REQUIRED_POLICY_REVOKE_DENIED"),
            @ApiResponse(responseCode = "404", description = "POLICY_NOT_FOUND")
    })
    @PostMapping("/api/v1/me/consents/revoke")
    public void revokeConsents(@AuthenticationPrincipal UserInfo userInfo,
                               @Valid @RequestBody MeRevokeConsentsRequest request) {
        meService.revokeConsents(userInfo.id(), MeRevokeConsentsCommand.from(request));
    }

    @Operation(summary = "푸시 알림 설정 조회")
    @GetMapping("/api/v1/me/push-notifications")
    public MePushNotificationsResponse pushNotifications(@AuthenticationPrincipal UserInfo userInfo) {
        return MePushNotificationsResponse.from(meService.pushNotifications(userInfo.id()));
    }

    @Operation(summary = "푸시 알림 설정 변경")
    @PatchMapping("/api/v1/me/push-notifications/{type}")
    public void changePushNotifications(@AuthenticationPrincipal UserInfo userInfo,
                                        @PathVariable NotificationType type,
                                        @Valid @RequestBody MeChangePushNotificationsRequest request) {
        meService.changePushNotifications(userInfo.id(), type, MeChangePushNotificationsCommand.from(request));
    }

    @Operation(summary = "프로필 공개 설정 조회")
    @GetMapping("/api/v1/me/profile/visibility-settings")
    public MeProfileVisibilitySettingsResponse profileVisibilitySettings(@AuthenticationPrincipal UserInfo userInfo) {
        return MeProfileVisibilitySettingsResponse.from(meService.profileVisibilitySettings(userInfo.id()));
    }

    @Operation(summary = "프로필 공개 설정 변경")
    @PatchMapping("/api/v1/me/profile/visibility-settings/{type}")
    public void changeProfileVisibilitySetting(@AuthenticationPrincipal UserInfo userInfo,
                                        @PathVariable DisclosureField type,
                                        @Valid @RequestBody MeChangeProfileVisibilitySettingRequest request) {
        meService.changeProfileVisibilitySetting(userInfo.id(), type, MeChangeProfileVisibilitySettingCommand.from(request));
    }

    @Operation(summary = "알림함 목록 조회")
    @GetMapping("/api/v1/me/app-notifications/feeds")
    public MeAppNotificationsFeedsResponse appNotificationsFeeds(@AuthenticationPrincipal UserInfo userInfo,
                                                                 @RequestParam(required = false) Boolean unreadOnly,
                                                                 @RequestParam(required = false) AppNotificationFeedType type,
                                                                 @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        return MeAppNotificationsFeedsResponse.from(meService.appNotificationsFeeds(userInfo.id(), unreadOnly, type, limit));
    }

    @Operation(summary = "알림 개별 읽음 처리")
    @ApiResponse(responseCode = "404", description = "APP_NOTIFICATION_NOT_FOUND")
    @PostMapping("/api/v1/me/app-notifications/{appNotificationId}/read")
    public void appNotificationsRead(@AuthenticationPrincipal UserInfo userInfo,
                                     @PathVariable String appNotificationId) {
        meService.appNotificationsRead(userInfo.id(), RequestId.toLong(appNotificationId, "appNotificationId"));
    }

    @Operation(summary = "알림 전체 읽음 처리")
    @PostMapping("/api/v1/me/app-notifications/read-all")
    public void appNotificationsReadAll(@AuthenticationPrincipal UserInfo userInfo,
                                        @Valid @RequestBody MeAppNotificationsReadAllRequest request) {
        meService.appNotificationsReadAll(userInfo.id(), MeAppNotificationsReadAllCommand.from(request));
    }

    @Operation(summary = "읽지 않은 알림 수 조회")
    @GetMapping("/api/v1/me/app-notifications/unread-count")
    public MeAppNotificationsUnreadCountResponse appNotificationsUnreadCount(@AuthenticationPrincipal UserInfo userInfo) {
        return MeAppNotificationsUnreadCountResponse.from(meService.appNotificationsUnreadCount(userInfo.id()));
    }

    @Operation(summary = "내 계정 상태 조회")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/me/status")
    public MeStatusResponse status(@AuthenticationPrincipal UserInfo userInfo) {
        return MeStatusResponse.from(meService.status(userInfo.id()));
    }

    @Operation(summary = "내 정보 조회")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/me/info")
    public MeInfoResponse info(@AuthenticationPrincipal UserInfo userInfo) {
        return MeInfoResponse.from(meService.info(userInfo.id()));
    }

    @Operation(summary = "내 성격 유형 조회")
    @ApiResponse(responseCode = "422", description = "PERSONA_NOT_FOUND")
    @GetMapping("/api/v1/me/personality-type")
    public MePersonalityTypeResponse personalityType(@AuthenticationPrincipal UserInfo userInfo) {
        return MePersonalityTypeResponse.from(meService.personalityType(userInfo.id()));
    }

    @Operation(summary = "망설임 목록 조회")
    @GetMapping("/api/v1/me/hesitations")
    public MeHesitationsResponse hesitations(@AuthenticationPrincipal UserInfo userInfo,
                                             @RequestParam HesitationDuration duration,
                                             @RequestParam HesitationStatus status) {
        return MeHesitationsResponse.from(meService.hesitations(userInfo.id(), duration, status));
    }

    @Operation(summary = "망설임 응답 제출")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_HESITATION_OWNER"),
            @ApiResponse(responseCode = "404", description = "HESITATION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "HESITATION_ALREADY_HANDLED"),
            @ApiResponse(responseCode = "422", description = "HESITATION_ANSWER_EMPTY, HESITATION_ANSWER_NOT_IN_OPTIONS")
    })
    @PostMapping("/api/v1/me/hesitations/{hesitationId}/answer")
    public void hesitationsAnswer(@AuthenticationPrincipal UserInfo userInfo,
                                  @PathVariable String hesitationId,
                                  @Valid @RequestBody MeHesitationsAnswerRequest request) {
        meService.hesitationsAnswer(userInfo.id(), RequestId.toLong(hesitationId, "hesitationId"), MeHesitationsAnswerCommand.from(request));
    }

    @Operation(summary = "내 프로필 조회")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/me/profile")
    public MeProfileResponse profile(@AuthenticationPrincipal UserInfo userInfo) {
        return MeProfileResponse.from(meService.profile(userInfo.id()));
    }

    @Operation(summary = "내 구매 상태 조회")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/me/purchases")
    public MePurchasesResponse purchases(@AuthenticationPrincipal UserInfo userInfo) {
        return MePurchasesResponse.from(meService.purchases(userInfo.id()));
    }

    @Operation(summary = "성향 문항 목록 조회")
    @GetMapping("/api/v1/me/tendency-questions")
    public MeTendencyQuestionsResponse tendencyQuestions() {
        return MeTendencyQuestionsResponse.from(meService.tendencyQuestions());
    }

    @Operation(summary = "성향 응답 제출")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "TENDENCY_QUESTION_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "TENDENCY_OPTION_NOT_IN_QUESTION")
    })
    @PutMapping("/api/v1/me/tendency-answers/{questionId}")
    public void submitTendencyAnswer(@AuthenticationPrincipal UserInfo userInfo,
                                     @PathVariable String questionId,
                                     @Valid @RequestBody MeSubmitTendencyAnswerRequest request) {
        meService.submitTendencyAnswer(userInfo.id(), RequestId.toLong(questionId, "questionId"), MeSubmitTendencyAnswerCommand.from(request));
    }

    @Operation(summary = "피드백 선택지 조회")
    @GetMapping("/api/v1/me/feedback/options")
    public MeFeedbackOptionsResponse feedbackOptions(@AuthenticationPrincipal UserInfo userInfo,
                                                     @RequestParam FeedbackType type) {
        return MeFeedbackOptionsResponse.from(meService.feedbackOptions(type));
    }

    @Operation(summary = "피드백 보내기")
    @PostMapping("/api/v1/me/feedback")
    public void sendFeedback(@AuthenticationPrincipal UserInfo userInfo,
                             @Parameter(hidden = true) @RequestHeader(value = AppBlockFilter.PLATFORM_HEADER, required = false) String appPlatform,
                             @Parameter(hidden = true) @RequestHeader(value = AppBlockFilter.VERSION_HEADER, required = false) String appVersion,
                             @Valid @RequestBody MeSendFeedbackRequest request) {
        meService.sendFeedback(userInfo.id(), MeSendFeedbackCommand.from(request), AppPlatform.fromHeader(appPlatform).orElse(null), AppVersion.parse(appVersion).orElse(null));
    }

    @Operation(summary = "닉네임 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "NICKNAME_ALREADY_USED"),
            @ApiResponse(responseCode = "422", description = "INVALID_NICKNAME")
    })
    @PutMapping("/api/v1/me/profile/nickname")
    public void changeProfileNickname(@AuthenticationPrincipal UserInfo userInfo,
                                      @Valid @RequestBody MeChangeProfileNicknameRequest request) {
        meService.changeProfileNickname(userInfo.id(), MeChangeProfileNicknameCommand.from(request));
    }

    @Operation(summary = "닉네임 중복 확인")
    @ApiResponse(responseCode = "422", description = "INVALID_NICKNAME")
    @PostMapping("/api/v1/me/profile/nickname/check")
    public MeCheckProfileNicknameResponse checkProfileNickname(@AuthenticationPrincipal UserInfo userInfo,
                                                               @Valid @RequestBody MeCheckProfileNicknameRequest request) {
        return MeCheckProfileNicknameResponse.from(meService.checkProfileNickname(userInfo.id(), MeCheckProfileNicknameCommand.from(request)));
    }

    @Operation(summary = "내 프로필 조회 v2")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v2/me/profile")
    public MeProfileV2Response profileV2(@AuthenticationPrincipal UserInfo userInfo) {
        return MeProfileV2Response.from(meService.profileV2(userInfo.id()));
    }

    @Operation(summary = "반려 뒤 학생증 다시 제출용 업로드 URL 발급")
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_TYPE")
    @PostMapping("/api/v1/me/school-verification/student-id/presign")
    public MeSchoolVerificationStudentIdPresignResponse schoolVerificationStudentIdPresign(@AuthenticationPrincipal UserInfo userInfo,
                                                                                           @Valid @RequestBody MeSchoolVerificationStudentIdPresignRequest request) {
        return MeSchoolVerificationStudentIdPresignResponse.from(meService.schoolVerificationStudentIdPresign(userInfo.id(), MeSchoolVerificationStudentIdPresignCommand.from(request)));
    }

    @Operation(summary = "반려 뒤 학생증 다시 제출", description = "학교는 가입 때 고른 학교로 고정한다. 가장 최근 제출과 key 가 같으면 상태와 관계없이 200 을 준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "SCHOOL_VERIFICATION_NOT_REJECTED"),
            @ApiResponse(responseCode = "422", description = "STUDENT_ID_PHOTO_NOT_FOUND, STUDENT_ID_PHOTO_TOO_LARGE")
    })
    @PostMapping("/api/v1/me/school-verification/student-id")
    public void schoolVerificationStudentIdSubmit(@AuthenticationPrincipal UserInfo userInfo,
                                                  @Valid @RequestBody MeSchoolVerificationStudentIdSubmitRequest request) {
        meService.schoolVerificationStudentIdSubmit(userInfo.id(), MeSchoolVerificationStudentIdSubmitCommand.from(request));
    }

    @Operation(summary = "트윈 업데이트 시작")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "TWIN_NOT_COMPLETED, TWIN_UPDATE_NOT_AVAILABLE")
    })
    @PostMapping("/api/v1/me/twin-update/start")
    public MeTwinUpdateResponse startTwinUpdate(@AuthenticationPrincipal UserInfo userInfo,
                                                @Valid @RequestBody MeStartTwinUpdateRequest request) {
        return MeTwinUpdateResponse.from(meService.startTwinUpdate(userInfo.id(), MeStartTwinUpdateCommand.from(request)));
    }

    @Operation(summary = "트윈 업데이트 회차 조회")
    @ApiResponse(responseCode = "404", description = "TWIN_UPDATE_NOT_FOUND")
    @GetMapping("/api/v1/me/twin-update/{updateId}")
    public MeTwinUpdateResponse twinUpdate(@AuthenticationPrincipal UserInfo userInfo,
                                           @PathVariable UUID updateId) {
        return MeTwinUpdateResponse.from(meService.twinUpdate(userInfo.id(), updateId));
    }

    @Operation(summary = "트윈 업데이트 설문 응답 제출")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "TWIN_UPDATE_NOT_FOUND, SURVEY_QUESTION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "TWIN_UPDATE_STALE, TWIN_UPDATE_ALREADY_COMPLETED"),
            @ApiResponse(responseCode = "422", description = "SURVEY_ANSWERS_INCOMPLETE")
    })
    @PostMapping("/api/v1/me/twin-update/survey-answers")
    public void submitTwinUpdateSurveyAnswer(@AuthenticationPrincipal UserInfo userInfo,
                                             @Valid @RequestBody MeSubmitTwinUpdateSurveyAnswerRequest request) {
        meService.submitTwinUpdateSurveyAnswer(userInfo.id(), MeSubmitTwinUpdateSurveyAnswerCommand.from(request));
    }

    @Operation(summary = "트윈 업데이트 관심사 저장")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "TWIN_UPDATE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "TWIN_UPDATE_STALE, TWIN_UPDATE_ALREADY_COMPLETED")
    })
    @PostMapping("/api/v1/me/twin-update/interests")
    public void saveTwinUpdateInterests(@AuthenticationPrincipal UserInfo userInfo,
                                        @Valid @RequestBody MeSaveTwinUpdateInterestsRequest request) {
        meService.saveTwinUpdateInterests(userInfo.id(), MeSaveTwinUpdateInterestsCommand.from(request));
    }

    @Operation(summary = "트윈 업데이트 AI 대화 시작")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "TWIN_UPDATE_NOT_FOUND, USER_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "TWIN_UPDATE_STALE, TWIN_UPDATE_ALREADY_COMPLETED")
    })
    @PostMapping("/api/v1/me/twin-update/ai-chat/start")
    public MeAiChatStartResponse startTwinUpdateAiChat(@AuthenticationPrincipal UserInfo userInfo,
                                                       @Valid @RequestBody MeStartTwinUpdateAiChatRequest request) {
        return MeAiChatStartResponse.from(meService.startTwinUpdateAiChat(userInfo.id(), MeStartTwinUpdateAiChatCommand.from(request)));
    }

    @Operation(summary = "트윈 업데이트 AI 대화 메시지 전송")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "TWIN_UPDATE_NOT_FOUND, AI_QUESTION_NOT_FOUND, USER_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "TWIN_UPDATE_STALE, TWIN_UPDATE_ALREADY_COMPLETED")
    })
    @PostMapping("/api/v1/me/twin-update/ai-chat/messages")
    public MeAiChatMessageResponse sendTwinUpdateAiChatMessage(@AuthenticationPrincipal UserInfo userInfo,
                                                               @Valid @RequestBody MeSendTwinUpdateAiChatMessageRequest request) {
        return MeAiChatMessageResponse.from(meService.sendTwinUpdateAiChatMessage(userInfo.id(), MeSendTwinUpdateAiChatMessageCommand.from(request)));
    }

    @Operation(summary = "트윈 업데이트 완료")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "TWIN_UPDATE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "TWIN_UPDATE_STALE"),
            @ApiResponse(responseCode = "422", description = "SURVEY_ANSWERS_INCOMPLETE, INTERESTS_NOT_SAVED")
    })
    @PostMapping("/api/v1/me/twin-update/complete")
    public MeCompleteTwinUpdateResponse completeTwinUpdate(@AuthenticationPrincipal UserInfo userInfo,
                                                           @Valid @RequestBody MeCompleteTwinUpdateRequest request) {
        return MeCompleteTwinUpdateResponse.from(meService.completeTwinUpdate(userInfo.id(), MeCompleteTwinUpdateCommand.from(request)));
    }
}

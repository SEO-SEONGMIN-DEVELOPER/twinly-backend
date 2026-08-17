package com.nidus.twinly.me.controller;

import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.me.domain.HesitationDuration;
import com.nidus.twinly.me.domain.HesitationStatus;
import com.nidus.twinly.me.dto.command.MeAppNotificationsReadAllCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileVisibilitySettingCommand;
import com.nidus.twinly.me.dto.command.MeChangePushNotificationsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsCommand;
import com.nidus.twinly.me.dto.command.MeHesitationsAnswerCommand;
import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsCommand;
import com.nidus.twinly.me.dto.request.MeAppNotificationsReadAllRequest;
import com.nidus.twinly.me.dto.request.MeChangeProfileVisibilitySettingRequest;
import com.nidus.twinly.me.dto.request.MeChangePushNotificationsRequest;
import com.nidus.twinly.me.dto.request.MeGrantConsentsRequest;
import com.nidus.twinly.me.dto.request.MeHesitationsAnswerRequest;
import com.nidus.twinly.me.dto.request.MeProfileRequest;
import com.nidus.twinly.me.dto.request.MeProfilePhotoCommitRequest;
import com.nidus.twinly.me.dto.request.MeProfilePhotoPresignRequest;
import com.nidus.twinly.me.dto.request.MeRevokeConsentsRequest;
import com.nidus.twinly.me.dto.response.MeAppNotificationsFeedsResponse;
import com.nidus.twinly.me.dto.response.MeAppNotificationsUnreadCountResponse;
import com.nidus.twinly.me.dto.response.MeConsentsResponse;
import com.nidus.twinly.me.dto.response.MeHesitationsResponse;
import com.nidus.twinly.me.dto.response.MePushNotificationsResponse;
import com.nidus.twinly.me.dto.response.MeProfileEditViewResponse;
import com.nidus.twinly.me.dto.response.MeProfileResponse;
import com.nidus.twinly.me.dto.response.MeProfilePhotoCommitResponse;
import com.nidus.twinly.me.dto.response.MeProfilePhotoPresignResponse;
import com.nidus.twinly.me.dto.response.MeProfileVisibilitySettingsResponse;
import com.nidus.twinly.me.dto.response.MeStatusResponse;
import com.nidus.twinly.me.dto.response.MeWithdrawResponse;
import com.nidus.twinly.me.service.MeService;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_TYPE")
    @PostMapping("/api/v1/me/profile/photo/presign")
    public MeProfilePhotoPresignResponse profilePhotoPresign(@AuthenticationPrincipal UserInfo userInfo,
                                                              @Valid @RequestBody MeProfilePhotoPresignRequest request) {
        return MeProfilePhotoPresignResponse.from(meService.profilePhotoPresign(userInfo.id(), MeProfilePhotoPresignCommand.from(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_KEY_OWNER"),
            @ApiResponse(responseCode = "422", description = "UPLOAD_NOT_COMPLETED")
    })
    @PostMapping("/api/v1/me/profile/photo/commit")
    public MeProfilePhotoCommitResponse profilePhotoCommit(@AuthenticationPrincipal UserInfo userInfo,
                                                            @Valid @RequestBody MeProfilePhotoCommitRequest request) {
        return MeProfilePhotoCommitResponse.from(meService.profilePhotoCommit(userInfo.id(), MeProfilePhotoCommitCommand.from(request)));
    }

    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @DeleteMapping("/api/v1/me")
    public MeWithdrawResponse withdraw(@AuthenticationPrincipal UserInfo userInfo) {
        return MeWithdrawResponse.from(meService.withdraw(userInfo.id()));
    }

    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/me/profile-edit-view")
    public MeProfileEditViewResponse profileEditView(@AuthenticationPrincipal UserInfo userInfo) {
        return MeProfileEditViewResponse.from(meService.profileEditView(userInfo.id()));
    }

    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @PatchMapping("/api/v1/me/profile")
    public void profile(@AuthenticationPrincipal UserInfo userInfo,
                        @Valid @RequestBody MeProfileRequest request) {
        meService.profile(userInfo.id(), MeProfileCommand.from(request));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "WITHDRAWAL_RECOVERY_EXPIRED")
    })
    @PostMapping("/api/v1/me/restore")
    public void restore(@AuthenticationPrincipal UserInfo userInfo) {
        meService.restore(userInfo.id());
    }

    @GetMapping("/api/v1/me/consents")
    public MeConsentsResponse consents(@AuthenticationPrincipal UserInfo userInfo) {
        return MeConsentsResponse.from(meService.consents(userInfo.id()));
    }

    @ApiResponse(responseCode = "404", description = "POLICY_NOT_FOUND")
    @PostMapping("/api/v1/me/consents")
    public void grantConsents(@AuthenticationPrincipal UserInfo userInfo,
                              @Valid @RequestBody MeGrantConsentsRequest request) {
        meService.grantConsents(userInfo.id(), MeGrantConsentsCommand.from(request));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "REQUIRED_POLICY_REVOKE_DENIED"),
            @ApiResponse(responseCode = "404", description = "POLICY_NOT_FOUND")
    })
    @PostMapping("/api/v1/me/consents/revoke")
    public void revokeConsents(@AuthenticationPrincipal UserInfo userInfo,
                               @Valid @RequestBody MeRevokeConsentsRequest request) {
        meService.revokeConsents(userInfo.id(), MeRevokeConsentsCommand.from(request));
    }

    @GetMapping("/api/v1/me/push-notifications")
    public MePushNotificationsResponse pushNotifications(@AuthenticationPrincipal UserInfo userInfo) {
        return MePushNotificationsResponse.from(meService.pushNotifications(userInfo.id()));
    }

    @PatchMapping("/api/v1/me/push-notifications/{type}")
    public void changePushNotifications(@AuthenticationPrincipal UserInfo userInfo,
                                        @PathVariable NotificationType type,
                                        @Valid @RequestBody MeChangePushNotificationsRequest request) {
        meService.changePushNotifications(userInfo.id(), type, MeChangePushNotificationsCommand.from(request));
    }

    @GetMapping("/api/v1/me/profile/visibility-settings")
    public MeProfileVisibilitySettingsResponse profileVisibilitySettings(@AuthenticationPrincipal UserInfo userInfo) {
        return MeProfileVisibilitySettingsResponse.from(meService.profileVisibilitySettings(userInfo.id()));
    }

    @PatchMapping("/api/v1/me/profile/visibility-settings/{type}")
    public void changeProfileVisibilitySetting(@AuthenticationPrincipal UserInfo userInfo,
                                        @PathVariable DisclosureField type,
                                        @Valid @RequestBody MeChangeProfileVisibilitySettingRequest request) {
        meService.changeProfileVisibilitySetting(userInfo.id(), type, MeChangeProfileVisibilitySettingCommand.from(request));
    }

    @GetMapping("/api/v1/me/app-notifications/feeds")
    public MeAppNotificationsFeedsResponse appNotificationsFeeds(@AuthenticationPrincipal UserInfo userInfo,
                                                                 @RequestParam(required = false) Boolean unreadOnly,
                                                                 @RequestParam(required = false) AppNotificationFeedType type,
                                                                 @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        return MeAppNotificationsFeedsResponse.from(meService.appNotificationsFeeds(userInfo.id(), unreadOnly, type, limit));
    }

    @ApiResponse(responseCode = "404", description = "APP_NOTIFICATION_NOT_FOUND")
    @PostMapping("/api/v1/me/app-notifications/{appNotificationId}/read")
    public void appNotificationsRead(@AuthenticationPrincipal UserInfo userInfo,
                                     @PathVariable String appNotificationId) {
        meService.appNotificationsRead(userInfo.id(), RequestId.toLong(appNotificationId, "appNotificationId"));
    }

    @PostMapping("/api/v1/me/app-notifications/read-all")
    public void appNotificationsReadAll(@AuthenticationPrincipal UserInfo userInfo,
                                        @Valid @RequestBody MeAppNotificationsReadAllRequest request) {
        meService.appNotificationsReadAll(userInfo.id(), MeAppNotificationsReadAllCommand.from(request));
    }

    @GetMapping("/api/v1/me/app-notifications/unread-count")
    public MeAppNotificationsUnreadCountResponse appNotificationsUnreadCount(@AuthenticationPrincipal UserInfo userInfo) {
        return MeAppNotificationsUnreadCountResponse.from(meService.appNotificationsUnreadCount(userInfo.id()));
    }

    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/me/status")
    public MeStatusResponse status(@AuthenticationPrincipal UserInfo userInfo) {
        return MeStatusResponse.from(meService.status(userInfo.id()));
    }

    @GetMapping("/api/v1/me/hesitations")
    public MeHesitationsResponse hesitations(@AuthenticationPrincipal UserInfo userInfo,
                                             @RequestParam HesitationDuration duration,
                                             @RequestParam HesitationStatus status) {
        return MeHesitationsResponse.from(meService.hesitations(userInfo.id(), duration, status));
    }

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

    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/api/v1/me/profile")
    public MeProfileResponse profile(@AuthenticationPrincipal UserInfo userInfo) {
        return MeProfileResponse.from(meService.profile(userInfo.id()));
    }
}

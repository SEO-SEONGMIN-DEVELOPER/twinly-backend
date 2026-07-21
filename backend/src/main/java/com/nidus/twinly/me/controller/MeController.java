package com.nidus.twinly.me.controller;

import com.nidus.twinly.me.dto.command.MeAppNotificationsReadAllCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileVisibilityCommand;
import com.nidus.twinly.me.dto.command.MeChangePushNotificationsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsCommand;
import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsCommand;
import com.nidus.twinly.me.dto.request.MeAppNotificationsReadAllRequest;
import com.nidus.twinly.me.dto.request.MeChangeProfileVisibilityRequest;
import com.nidus.twinly.me.dto.request.MeChangePushNotificationsRequest;
import com.nidus.twinly.me.dto.request.MeGrantConsentsRequest;
import com.nidus.twinly.me.dto.request.MeProfileRequest;
import com.nidus.twinly.me.dto.request.MeProfilePhotoCommitRequest;
import com.nidus.twinly.me.dto.request.MeProfilePhotoPresignRequest;
import com.nidus.twinly.me.dto.request.MeRevokeConsentsRequest;
import com.nidus.twinly.me.dto.response.MeAppNotificationsFeedsResponse;
import com.nidus.twinly.me.dto.response.MeAppNotificationsUnreadCountResponse;
import com.nidus.twinly.me.dto.response.MeConsentsResponse;
import com.nidus.twinly.me.dto.response.MePushNotificationsResponse;
import com.nidus.twinly.me.dto.response.MeProfileEditViewResponse;
import com.nidus.twinly.me.dto.response.MeProfilePhotoCommitResponse;
import com.nidus.twinly.me.dto.response.MeProfilePhotoPresignResponse;
import com.nidus.twinly.me.dto.response.MeProfileVisibilityResponse;
import com.nidus.twinly.me.dto.response.MeWithdrawResponse;
import com.nidus.twinly.me.service.MeService;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.dto.header.UserInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/api/v1/me/profile/photo/presign")
    public MeProfilePhotoPresignResponse profilePhotoPresign(@CurrentUser UserInfo userInfo,
                                                              @Valid @RequestBody MeProfilePhotoPresignRequest request) {
        return MeProfilePhotoPresignResponse.from(meService.profilePhotoPresign(userInfo.id(), MeProfilePhotoPresignCommand.from(request)));
    }

    @PostMapping("/api/v1/me/profile/photo/commit")
    public MeProfilePhotoCommitResponse profilePhotoCommit(@CurrentUser UserInfo userInfo,
                                                            @Valid @RequestBody MeProfilePhotoCommitRequest request) {
        return MeProfilePhotoCommitResponse.from(meService.profilePhotoCommit(userInfo.id(), MeProfilePhotoCommitCommand.from(request)));
    }

    @DeleteMapping("/api/v1/me")
    public MeWithdrawResponse withdraw(@CurrentUser UserInfo userInfo) {
        return MeWithdrawResponse.from(meService.withdraw(userInfo.id()));
    }

    @GetMapping("/api/v1/me/profile-edit-view")
    public MeProfileEditViewResponse profileEditView(@CurrentUser UserInfo userInfo) {
        return MeProfileEditViewResponse.from(meService.profileEditView(userInfo.id()));
    }

    @PatchMapping("/api/v1/me/profile")
    public void profile(@CurrentUser UserInfo userInfo,
                        @Valid @RequestBody MeProfileRequest request) {
        meService.profile(userInfo.id(), MeProfileCommand.from(request));
    }

    @PostMapping("/api/v1/me/restore")
    public void restore(@CurrentUser UserInfo userInfo) {
        meService.restore(userInfo.id());
    }

    @GetMapping("/api/v1/me/consents")
    public MeConsentsResponse consents(@CurrentUser UserInfo userInfo) {
        return MeConsentsResponse.from(meService.consents(userInfo.id()));
    }

    @PostMapping("/api/v1/me/consents")
    public void grantConsents(@CurrentUser UserInfo userInfo,
                              @Valid @RequestBody MeGrantConsentsRequest request) {
        meService.grantConsents(userInfo.id(), MeGrantConsentsCommand.from(request));
    }

    @DeleteMapping("/api/v1/me/consents")
    public void revokeConsents(@CurrentUser UserInfo userInfo,
                               @Valid @RequestBody MeRevokeConsentsRequest request) {
        meService.revokeConsents(userInfo.id(), MeRevokeConsentsCommand.from(request));
    }

    @GetMapping("/api/v1/me/push-notifications")
    public MePushNotificationsResponse pushNotifications(@CurrentUser UserInfo userInfo) {
        return MePushNotificationsResponse.from(meService.pushNotifications(userInfo.id()));
    }

    @PatchMapping("/api/v1/me/push-notifications/{type}")
    public void changePushNotifications(@CurrentUser UserInfo userInfo,
                                        @PathVariable NotificationType type,
                                        @Valid @RequestBody MeChangePushNotificationsRequest request) {
        meService.changePushNotifications(userInfo.id(), type, MeChangePushNotificationsCommand.from(request));
    }

    @GetMapping("/api/v1/me/profile/visibility")
    public MeProfileVisibilityResponse profileVisibility(@CurrentUser UserInfo userInfo) {
        return MeProfileVisibilityResponse.from(meService.profileVisibility(userInfo.id()));
    }

    @PatchMapping("/api/v1/me/profile/visibility/{type}")
    public void changeProfileVisibility(@CurrentUser UserInfo userInfo,
                                        @PathVariable DisclosureField type,
                                        @Valid @RequestBody MeChangeProfileVisibilityRequest request) {
        meService.changeProfileVisibility(userInfo.id(), type, MeChangeProfileVisibilityCommand.from(request));
    }

    @GetMapping("/api/v1/me/app-notifications/feeds")
    public MeAppNotificationsFeedsResponse appNotificationsFeeds(@CurrentUser UserInfo userInfo,
                                                                 @RequestParam(required = false) Boolean unreadOnly,
                                                                 @RequestParam(required = false) AppNotificationFeedType type,
                                                                 @RequestParam(required = false) Integer limit) {
        return MeAppNotificationsFeedsResponse.from(meService.appNotificationsFeeds(userInfo.id(), unreadOnly, type, limit));
    }

    @PostMapping("/api/v1/me/app-notifications/{appNotificationId}/read")
    public void appNotificationsRead(@CurrentUser UserInfo userInfo,
                                     @PathVariable Long appNotificationId) {
        meService.appNotificationsRead(userInfo.id(), appNotificationId);
    }

    @PostMapping("/api/v1/me/app-notifications/read-all")
    public void appNotificationsReadAll(@CurrentUser UserInfo userInfo,
                                        @Valid @RequestBody MeAppNotificationsReadAllRequest request) {
        meService.appNotificationsReadAll(userInfo.id(), MeAppNotificationsReadAllCommand.from(request));
    }

    @GetMapping("/api/v1/me/app-notifications/unread-count")
    public MeAppNotificationsUnreadCountResponse appNotificationsUnreadCount(@CurrentUser UserInfo userInfo) {
        return MeAppNotificationsUnreadCountResponse.from(meService.appNotificationsUnreadCount(userInfo.id()));
    }
}

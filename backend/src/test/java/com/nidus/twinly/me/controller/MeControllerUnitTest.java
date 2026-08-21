package com.nidus.twinly.me.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.photo.PhotoPosInfo;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.common.presign.RequiredHeaders;
import com.nidus.twinly.me.domain.HesitationDuration;
import com.nidus.twinly.me.domain.HesitationStatus;
import com.nidus.twinly.me.dto.command.MeAppNotificationsReadAllCommand;
import com.nidus.twinly.me.dto.command.MeChangeProfileVisibilitySettingCommand;
import com.nidus.twinly.me.dto.command.MeChangePushNotificationsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsCommand;
import com.nidus.twinly.me.dto.command.MeGrantConsentsItemCommand;
import com.nidus.twinly.me.dto.command.MeHesitationsAnswerCommand;
import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsCommand;
import com.nidus.twinly.me.dto.command.MeRevokeConsentsItemCommand;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsItemResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsProfileTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsUnreadCountResult;
import com.nidus.twinly.me.dto.result.MeConsentsItemResult;
import com.nidus.twinly.me.dto.result.MeConsentsResult;
import com.nidus.twinly.me.dto.result.MeHesitationsResult;
import com.nidus.twinly.me.dto.result.MeProfileEditViewResult;
import com.nidus.twinly.me.dto.result.MeProfileResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoCommitResult;
import com.nidus.twinly.me.dto.result.MeProfilePhotoPresignResult;
import com.nidus.twinly.me.dto.result.MeProfileVisibilitySettingsResult;
import com.nidus.twinly.me.dto.result.MePushNotificationsResult;
import com.nidus.twinly.me.dto.result.MePushNotificationsSettingsResult;
import com.nidus.twinly.me.dto.result.MeStatusReportResult;
import com.nidus.twinly.me.dto.result.MeStatusResult;
import com.nidus.twinly.me.dto.result.MeStatusWithdrawalResult;
import com.nidus.twinly.me.dto.result.MeWithdrawResult;
import com.nidus.twinly.me.service.MeService;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;
import com.nidus.twinly.notification.domain.NotificationType;
import com.nidus.twinly.user.domain.DisclosureField;
import com.nidus.twinly.user.dto.header.UserInfo;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.nidus.twinly.common.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerUnitTest {

    private static final String BEARER = "Bearer access-token";
    private static final Long ME = 1L;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MeService meService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        given(userService.resolveByAccessToken(anyString()))
                .willReturn(new UserInfo(ME));
    }

    // ---------------------------------------------------------------- 프로필 사진

    @Test
    @DisplayName("프로필 사진 presign 성공 시 200과 업로드 정보를 반환하고 contentType으로 서비스를 호출한다")
    void profilePhotoPresign_success() throws Exception {
        // given: 서비스가 presign 결과를 반환
        Instant expiresAt = Instant.parse("2026-07-26T00:05:00Z");
        given(meService.profilePhotoPresign(eq(ME), any()))
                .willReturn(new MeProfilePhotoPresignResult(
                        "https://s3/upload", "profile/1/uuid", "PUT",
                        new RequiredHeaders("image/jpeg"), 10485760, expiresAt));

        // when: presign API 호출
        var result = mockMvc.perform(post("/api/v1/me/profile/photo/presign")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contentType":"image/jpeg"}
                        """));

        // then: 200 반환 + 업로드 정보 JSON + contentType 커맨드로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3/upload"))
                .andExpect(jsonPath("$.key").value("profile/1/uuid"))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.requiredHeaders.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.maxBytes").value(10485760))
                .andExpect(jsonPath("$.expiresAt").value("2026-07-26T00:05:00Z"));
        then(meService).should().profilePhotoPresign(ME, new MeProfilePhotoPresignCommand("image/jpeg"));
    }

    @Test
    @DisplayName("프로필 사진 presign 요청에 contentType이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void profilePhotoPresign_without_contentType_returns_400() throws Exception {
        // when: contentType 없이 presign API 호출
        var result = mockMvc.perform(post("/api/v1/me/profile/photo/presign")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).profilePhotoPresign(anyLong(), any());
    }

    @Test
    @DisplayName("프로필 사진 commit 성공 시 200과 사진 URL·위치 정보를 반환하고 key·위치로 서비스를 호출한다")
    void profilePhotoCommit_success() throws Exception {
        // given: 서비스가 commit 결과를 반환
        PhotoPosInfo position = new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 300, 400);
        given(meService.profilePhotoCommit(eq(ME), any()))
                .willReturn(new MeProfilePhotoCommitResult("https://cdn/profile.jpg", position));

        // when: commit API 호출
        var result = mockMvc.perform(post("/api/v1/me/profile/photo/commit")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key":"profile/1/uuid","position":{"startPos":{"x":10,"y":20},"width":300,"height":400}}
                        """));

        // then: 200 반환 + 사진 URL·위치 JSON + key·위치 커맨드로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://cdn/profile.jpg"))
                .andExpect(jsonPath("$.position.startPos.x").value(10))
                .andExpect(jsonPath("$.position.startPos.y").value(20))
                .andExpect(jsonPath("$.position.width").value(300))
                .andExpect(jsonPath("$.position.height").value(400));
        then(meService).should().profilePhotoCommit(ME, new MeProfilePhotoCommitCommand("profile/1/uuid", position));
    }

    @Test
    @DisplayName("프로필 사진 commit 요청의 position이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void profilePhotoCommit_without_position_returns_400() throws Exception {
        // when: position 없이 commit API 호출
        var result = mockMvc.perform(post("/api/v1/me/profile/photo/commit")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key":"profile/1/uuid"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).profilePhotoCommit(anyLong(), any());
    }

    // ---------------------------------------------------------------- 탈퇴 / 복구

    @Test
    @DisplayName("탈퇴 신청 성공 시 200과 복구 가능 시각을 반환하고 인증 유저 id로 서비스를 호출한다")
    void withdraw_success() throws Exception {
        // given: 서비스가 복구 가능 시각을 반환
        given(meService.withdraw(ME))
                .willReturn(new MeWithdrawResult(Instant.parse("2026-08-10T00:00:00Z")));

        // when: 탈퇴 API 호출
        var result = mockMvc.perform(delete("/api/v1/me")
                .header("Authorization", BEARER));

        // then: 200 반환 + recoverableUntil JSON + 인증 유저 id로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.recoverableUntil").value("2026-08-10T00:00:00Z"));
        then(meService).should().withdraw(ME);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void withdraw_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 탈퇴 API 호출
        var result = mockMvc.perform(delete("/api/v1/me"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(meService).should(never()).withdraw(anyLong());
    }

    @Test
    @DisplayName("탈퇴 복구 성공 시 200을 반환하고 인증 유저 id로 서비스를 호출한다")
    void restore_success() throws Exception {
        // when: 복구 API 호출
        var result = mockMvc.perform(post("/api/v1/me/restore")
                .header("Authorization", BEARER));

        // then: 200 반환 + 인증 유저 id로 위임
        result.andExpect(status().isOk());
        then(meService).should().restore(ME);
    }

    // ---------------------------------------------------------------- 프로필

    @Test
    @DisplayName("프로필 수정 화면 조회 시 userId를 문자열로 직렬화한 JSON을 반환한다")
    void profileEditView_success() throws Exception {
        // given: 서비스가 프로필 수정 화면 정보를 반환
        given(meService.profileEditView(ME))
                .willReturn(new MeProfileEditViewResult(1L, "홍", "길동", "니두스", "2020123", "2000-01-01",
                        new ProfilePhotoInfo("profile/1/key", "https://cdn/p.jpg", new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200))));

        // when: 프로필 수정 화면 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/profile-edit-view")
                .header("Authorization", BEARER));

        // then: 200 반환 + userId는 문자열로 직렬화
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("1"))
                .andExpect(jsonPath("$.familyName").value("홍"))
                .andExpect(jsonPath("$.givenName").value("길동"))
                .andExpect(jsonPath("$.affiliation").value("니두스"))
                .andExpect(jsonPath("$.affiliationNumber").value("2020123"))
                .andExpect(jsonPath("$.birthDate").value("2000-01-01"))
                .andExpect(jsonPath("$.profilePhoto.key").value("profile/1/key"))
                .andExpect(jsonPath("$.profilePhoto.photoUrl").value("https://cdn/p.jpg"))
                .andExpect(jsonPath("$.profilePhoto.position.startPos.x").value(10))
                .andExpect(jsonPath("$.profilePhoto.position.startPos.y").value(20))
                .andExpect(jsonPath("$.profilePhoto.position.width").value(100))
                .andExpect(jsonPath("$.profilePhoto.position.height").value(200));
    }

    @Test
    @DisplayName("프로필 수정 성공 시 200을 반환하고 affiliation 커맨드로 서비스를 호출한다")
    void profile_success() throws Exception {
        // when: 소속 변경 API 호출
        var result = mockMvc.perform(patch("/api/v1/me/profile")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"affiliation":"니두스"}
                        """));

        // then: 200 반환 + affiliation 커맨드로 위임
        result.andExpect(status().isOk());
        then(meService).should().profile(ME, new MeProfileCommand("니두스"));
    }

    @Test
    @DisplayName("프로필 수정 요청에 affiliation이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void profile_without_affiliation_returns_400() throws Exception {
        // when: affiliation 없이 소속 변경 API 호출
        var result = mockMvc.perform(patch("/api/v1/me/profile")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).profile(anyLong(), any());
    }

    // ---------------------------------------------------------------- 약관 동의

    @Test
    @DisplayName("약관 동의 목록 조회 시 version을 문자열로 직렬화한 JSON을 반환한다")
    void consents_success() throws Exception {
        // given: 서비스가 약관 동의 항목 1건을 반환
        given(meService.consents(ME))
                .willReturn(new MeConsentsResult(List.of(new MeConsentsItemResult(
                        "terms_of_service", "서비스 이용약관", "2", "https://policy/tos", true, true, true,
                        Instant.parse("2026-07-01T00:00:00Z")))));

        // when: 약관 동의 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/consents")
                .header("Authorization", BEARER));

        // then: 200 반환 + version은 문자열로 직렬화
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[0].policyId").value("terms_of_service"))
                .andExpect(jsonPath("$.consents[0].title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.consents[0].version").value("2"))
                .andExpect(jsonPath("$.consents[0].url").value("https://policy/tos"))
                .andExpect(jsonPath("$.consents[0].isRequired").value(true))
                .andExpect(jsonPath("$.consents[0].isGranted").value(true))
                .andExpect(jsonPath("$.consents[0].grantedAt").value("2026-07-01T00:00:00Z"));
    }

    @Test
    @DisplayName("약관 동의 성공 시 200을 반환하고 policyId·version 커맨드로 서비스를 호출한다")
    void grantConsents_success() throws Exception {
        // when: 약관 동의 API 호출 (version은 문자열로 전달)
        var result = mockMvc.perform(post("/api/v1/me/consents")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants":[{"policyId":"terms_of_service","version":"2"}]}
                        """));

        // then: 200 반환 + policyId·version 커맨드로 위임
        result.andExpect(status().isOk());
        then(meService).should().grantConsents(ME,
                new MeGrantConsentsCommand(List.of(new MeGrantConsentsItemCommand("terms_of_service", "2"))));
    }

    @Test
    @DisplayName("약관 동의 요청의 policyId가 공백이면 400을 반환하고 서비스를 호출하지 않는다")
    void grantConsents_with_blank_policyId_returns_400() throws Exception {
        // when: policyId가 공백인 약관 동의 API 호출
        var result = mockMvc.perform(post("/api/v1/me/consents")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants":[{"policyId":"  ","version":"2"}]}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).grantConsents(anyLong(), any());
    }

    @Test
    @DisplayName("약관 동의 철회 성공 시 200을 반환하고 policyId·version 커맨드로 서비스를 호출한다")
    void revokeConsents_success() throws Exception {
        // when: 약관 동의 철회 API 호출
        var result = mockMvc.perform(post("/api/v1/me/consents/revoke")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"grants":[{"policyId":"marketing","version":"1"}]}
                        """));

        // then: 200 반환 + policyId·version 커맨드로 위임
        result.andExpect(status().isOk());
        then(meService).should().revokeConsents(ME,
                new MeRevokeConsentsCommand(List.of(new MeRevokeConsentsItemCommand("marketing", "1"))));
    }

    // ---------------------------------------------------------------- 푸시 알림 설정

    @Test
    @DisplayName("푸시 알림 설정 조회 시 타입별 on/off를 담은 JSON을 반환한다")
    void pushNotifications_success() throws Exception {
        // given: 서비스가 푸시 알림 설정을 반환
        given(meService.pushNotifications(ME))
                .willReturn(new MePushNotificationsResult(new MePushNotificationsSettingsResult(true, false, true)));

        // when: 푸시 알림 설정 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/push-notifications")
                .header("Authorization", BEARER));

        // then: 200 반환 + 타입별 on/off JSON
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.pushNotificationSettings.event").value(true))
                .andExpect(jsonPath("$.pushNotificationSettings.chat").value(false))
                .andExpect(jsonPath("$.pushNotificationSettings.marketing").value(true));
    }

    @Test
    @DisplayName("푸시 알림 설정 변경 성공 시 200을 반환하고 경로의 타입과 isEnabled로 서비스를 호출한다")
    void changePushNotifications_success() throws Exception {
        // when: CHAT 타입 푸시 알림 off 요청
        var result = mockMvc.perform(patch("/api/v1/me/push-notifications/{type}", "CHAT")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isEnabled":false}
                        """));

        // then: 200 반환 + 경로 타입·커맨드로 위임
        result.andExpect(status().isOk());
        then(meService).should().changePushNotifications(ME, NotificationType.CHAT,
                new MeChangePushNotificationsCommand(false));
    }

    @Test
    @DisplayName("푸시 알림 설정 변경 시 존재하지 않는 타입이면 400을 반환하고 서비스를 호출하지 않는다")
    void changePushNotifications_with_unknown_type_returns_400() throws Exception {
        // when: 존재하지 않는 알림 타입으로 변경 요청
        var result = mockMvc.perform(patch("/api/v1/me/push-notifications/{type}", "UNKNOWN")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isEnabled":false}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).changePushNotifications(anyLong(), any(), any());
    }

    @Test
    @DisplayName("푸시 알림 설정 변경 요청에 isEnabled가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void changePushNotifications_without_isEnabled_returns_400() throws Exception {
        // when: isEnabled 없이 변경 요청
        var result = mockMvc.perform(patch("/api/v1/me/push-notifications/{type}", "CHAT")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).changePushNotifications(anyLong(), any(), any());
    }

    // ---------------------------------------------------------------- 프로필 공개 설정

    @Test
    @DisplayName("프로필 공개 설정 조회 시 항목별 공개 여부 JSON을 반환한다")
    void profileVisibility_success() throws Exception {
        // given: 서비스가 공개 설정을 반환
        given(meService.profileVisibilitySettings(ME))
                .willReturn(new MeProfileVisibilitySettingsResult(true, false));

        // when: 프로필 공개 설정 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/profile/visibility-settings")
                .header("Authorization", BEARER));

        // then: 200 반환 + 항목별 공개 여부 JSON
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliationVisible").value(true))
                .andExpect(jsonPath("$.affiliationNumberVisible").value(false));
    }

    @Test
    @DisplayName("프로필 공개 설정 변경 성공 시 200을 반환하고 경로의 항목과 isVisible로 서비스를 호출한다")
    void changeProfileVisibility_success() throws Exception {
        // when: AFFILIATION 항목을 공개로 변경 요청
        var result = mockMvc.perform(patch("/api/v1/me/profile/visibility-settings/{type}", "AFFILIATION")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isVisible":true}
                        """));

        // then: 200 반환 + 경로 항목·커맨드로 위임
        result.andExpect(status().isOk());
        then(meService).should().changeProfileVisibilitySetting(ME, DisclosureField.AFFILIATION,
                new MeChangeProfileVisibilitySettingCommand(true));
    }

    @Test
    @DisplayName("프로필 공개 설정 변경 시 존재하지 않는 항목이면 400을 반환하고 서비스를 호출하지 않는다")
    void changeProfileVisibility_with_unknown_type_returns_400() throws Exception {
        // when: 존재하지 않는 공개 항목으로 변경 요청
        var result = mockMvc.perform(patch("/api/v1/me/profile/visibility-settings/{type}", "NICKNAME")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isVisible":true}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).changeProfileVisibilitySetting(anyLong(), any(), any());
    }

    // ---------------------------------------------------------------- 앱 알림

    @Test
    @DisplayName("앱 알림 피드 조회 시 쿼리 파라미터를 그대로 위임하고 id·target을 문자열로 직렬화한다")
    void appNotificationsFeeds_success() throws Exception {
        // given: 서비스가 프로필 타깃 알림 1건을 반환
        given(meService.appNotificationsFeeds(eq(ME), any(), any(), any()))
                .willReturn(new MeAppNotificationsFeedsResult(3, List.of(new MeAppNotificationsFeedsItemResult(
                        10L, AppNotificationFeedType.FRIEND, "제목", "본문",
                        new MeAppNotificationsFeedsProfileTargetResult("profile", 5L),
                        false, Instant.parse("2026-07-20T09:00:00Z")))));

        // when: 필터 조건을 붙여 앱 알림 피드 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/app-notifications/feeds")
                .header("Authorization", BEARER)
                .param("unreadOnly", "true")
                .param("type", "friend")
                .param("limit", "5"));

        // then: 200 반환 + id·targetUserId는 문자열, type은 소문자 별칭으로 직렬화 + 파라미터 그대로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.appNotificationFeeds[0].id").value("10"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].type").value("friend"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].title").value("제목"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].body").value("본문"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.kind").value("profile"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].target.userId").value("5"))
                .andExpect(jsonPath("$.appNotificationFeeds[0].isRead").value(false))
                .andExpect(jsonPath("$.appNotificationFeeds[0].createdAt").value("2026-07-20T09:00:00Z"));
        then(meService).should().appNotificationsFeeds(ME, true, AppNotificationFeedType.FRIEND, 5);
    }

    @Test
    @DisplayName("앱 알림 피드 조회 시 필터 파라미터가 없으면 null로 위임한다")
    void appNotificationsFeeds_without_params_delegates_null() throws Exception {
        // given: 서비스가 빈 피드를 반환
        given(meService.appNotificationsFeeds(eq(ME), any(), any(), any()))
                .willReturn(new MeAppNotificationsFeedsResult(0, List.of()));

        // when: 파라미터 없이 앱 알림 피드 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/app-notifications/feeds")
                .header("Authorization", BEARER));

        // then: 200 반환 + 세 파라미터 모두 null로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.appNotificationFeeds").isEmpty());
        then(meService).should().appNotificationsFeeds(ME, null, null, null);
    }

    @Test
    @DisplayName("앱 알림 읽음 처리 성공 시 200을 반환하고 경로의 id를 Long으로 변환해 서비스를 호출한다")
    void appNotificationsRead_success() throws Exception {
        // when: 문자열 id로 앱 알림 읽음 처리 API 호출
        var result = mockMvc.perform(post("/api/v1/me/app-notifications/{appNotificationId}/read", "77")
                .header("Authorization", BEARER));

        // then: 200 반환 + Long으로 변환된 id로 위임
        result.andExpect(status().isOk());
        then(meService).should().appNotificationsRead(ME, 77L);
    }

    @Test
    @DisplayName("앱 알림 읽음 처리 시 경로 id가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void appNotificationsRead_with_non_numeric_id_returns_400() throws Exception {
        // when: 숫자가 아닌 id로 앱 알림 읽음 처리 API 호출
        var result = mockMvc.perform(post("/api/v1/me/app-notifications/{appNotificationId}/read", "abc")
                .header("Authorization", BEARER));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(meService).should(never()).appNotificationsRead(anyLong(), anyLong());
    }

    @Test
    @DisplayName("앱 알림 전체 읽음 처리 성공 시 200을 반환하고 lastAppNotificationId 커맨드로 서비스를 호출한다")
    void appNotificationsReadAll_success() throws Exception {
        // when: 마지막 알림 id를 문자열로 담아 전체 읽음 처리 API 호출
        var result = mockMvc.perform(post("/api/v1/me/app-notifications/read-all")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lastAppNotificationId":"100"}
                        """));

        // then: 200 반환 + Long으로 역직렬화된 커맨드로 위임
        result.andExpect(status().isOk());
        then(meService).should().appNotificationsReadAll(ME, new MeAppNotificationsReadAllCommand(100L));
    }

    @Test
    @DisplayName("앱 알림 전체 읽음 처리 요청에 lastAppNotificationId가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void appNotificationsReadAll_without_lastId_returns_400() throws Exception {
        // when: lastAppNotificationId 없이 전체 읽음 처리 API 호출
        var result = mockMvc.perform(post("/api/v1/me/app-notifications/read-all")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).appNotificationsReadAll(anyLong(), any());
    }

    @Test
    @DisplayName("앱 알림 미읽음 개수 조회 시 개수를 담은 JSON을 반환한다")
    void appNotificationsUnreadCount_success() throws Exception {
        // given: 서비스가 미읽음 개수를 반환
        given(meService.appNotificationsUnreadCount(ME))
                .willReturn(new MeAppNotificationsUnreadCountResult(7));

        // when: 미읽음 개수 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/app-notifications/unread-count")
                .header("Authorization", BEARER));

        // then: 200 반환 + 개수 JSON
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(7));
    }

    // ---------------------------------------------------------------- 상태

    @Test
    @DisplayName("내 상태 조회 시 탈퇴·신고 상태를 담은 JSON을 반환한다")
    void status_success() throws Exception {
        // given: 서비스가 탈퇴 신청·신고 처리 상태를 반환
        given(meService.status(ME))
                .willReturn(new MeStatusResult(
                        new MeStatusWithdrawalResult(true, Instant.parse("2026-08-10T00:00:00Z")),
                        new MeStatusReportResult(true, List.of("SPAM", "HARASSMENT"))));

        // when: 내 상태 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/status")
                .header("Authorization", BEARER));

        // then: 200 반환 + 탈퇴·신고 상태 JSON
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal.isDeleted").value(true))
                .andExpect(jsonPath("$.withdrawal.recoverableUntil").value("2026-08-10T00:00:00Z"))
                .andExpect(jsonPath("$.report.isReported").value(true))
                .andExpect(jsonPath("$.report.reasons[0]").value("SPAM"))
                .andExpect(jsonPath("$.report.reasons[1]").value("HARASSMENT"));
    }

    // ---------------------------------------------------------------- 망설임

    @Test
    @DisplayName("망설임 목록 조회 시 duration·status를 그대로 위임하고 id 목록을 문자열로 직렬화한다")
    void hesitations_success() throws Exception {
        // given: 서비스가 오늘자 미답변 망설임 id 목록을 반환
        given(meService.hesitations(eq(ME), any(), any()))
                .willReturn(new MeHesitationsResult(LocalDate.of(2026, 7, 26), List.of(7L, 8L)));

        // when: duration·status를 붙여 망설임 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/hesitations")
                .header("Authorization", BEARER)
                .param("duration", "TODAY")
                .param("status", "UNANSWERED"));

        // then: 200 반환 + id 목록은 문자열로 직렬화 + 파라미터 그대로 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-07-26"))
                .andExpect(jsonPath("$.hesitationIds[0]").value("7"))
                .andExpect(jsonPath("$.hesitationIds[1]").value("8"));
        then(meService).should().hesitations(ME, HesitationDuration.TODAY, HesitationStatus.UNANSWERED);
    }

    @Test
    @DisplayName("망설임 목록 조회 시 duration이 허용되지 않는 값이면 400을 반환하고 서비스를 호출하지 않는다")
    void hesitations_with_invalid_duration_returns_400() throws Exception {
        // when: 허용되지 않는 duration으로 망설임 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/hesitations")
                .header("Authorization", BEARER)
                .param("duration", "WEEK")
                .param("status", "ALL"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).hesitations(anyLong(), any(), any());
    }

    @Test
    @DisplayName("망설임 목록 조회 시 필수 쿼리 파라미터가 빠지면 400을 반환하고 서비스를 호출하지 않는다")
    void hesitations_with_missing_required_param_returns_400() throws Exception {
        // when: 필수 파라미터 duration 없이 망설임 목록 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/hesitations")
                .header("Authorization", BEARER)
                .param("status", "ALL"));

        // then: 클라이언트 입력 오류이므로 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).hesitations(anyLong(), any(), any());
    }

    @Test
    @DisplayName("망설임 답변 성공 시 200을 반환하고 경로 id와 answer·skipped 커맨드로 서비스를 호출한다")
    void hesitationsAnswer_success() throws Exception {
        // when: 선택지 중 하나를 골라 망설임 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/me/hesitations/{hesitationId}/answer", "42")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answer":"바로 연락한다","skipped":false}
                        """));

        // then: 200 반환 + Long으로 변환된 id·커맨드로 위임
        result.andExpect(status().isOk());
        then(meService).should().hesitationsAnswer(ME, 42L,
                new MeHesitationsAnswerCommand("바로 연락한다", false));
    }

    @Test
    @DisplayName("망설임 답변 요청에 skipped가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void hesitationsAnswer_without_skipped_returns_400() throws Exception {
        // when: skipped 없이 망설임 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/me/hesitations/{hesitationId}/answer", "42")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answer":"바로 연락한다"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(meService).should(never()).hesitationsAnswer(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("망설임 답변 시 경로 id가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void hesitationsAnswer_with_non_numeric_id_returns_400() throws Exception {
        // when: 숫자가 아닌 id로 망설임 답변 API 호출
        var result = mockMvc.perform(post("/api/v1/me/hesitations/{hesitationId}/answer", "abc")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answer":"바로 연락한다","skipped":false}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(meService).should(never()).hesitationsAnswer(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("프로필 수정 시 affiliation이 공백뿐이면 400을 반환하고 서비스를 호출하지 않는다")
    void profile_with_blank_affiliation_returns_400() throws Exception {
        // when: 공백만 있는 소속으로 프로필 수정 API 호출
        var result = mockMvc.perform(patch("/api/v1/me/profile")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"affiliation\":\"   \"}"));

        // then: 400 INVALID_REQUEST 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(meService).should(never()).profile(anyLong(), any());
    }

    @Test
    @DisplayName("앱 알림 목록 조회 시 limit이 허용 범위(1~100) 밖이면 400을 반환하고 서비스를 호출하지 않는다")
    void appNotificationsFeeds_with_out_of_range_limit_returns_400() throws Exception {
        // when & then: 0과 상한 초과 모두 입력 단계에서 막힌다
        for (String limit : List.of("0", "101")) {
            mockMvc.perform(get("/api/v1/me/app-notifications/feeds")
                            .header("Authorization", BEARER)
                            .param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        then(meService).should(never()).appNotificationsFeeds(anyLong(), any(), any(), any());
    }

    // ---------------------------------------------------------------- 내 프로필 조회

    @Test
    @DisplayName("내 프로필 조회 성공 시 200과 함께 userId를 문자열로 직렬화한 JSON을 반환한다")
    void myProfile_success() throws Exception {
        // given: 서비스가 내 프로필 정보를 반환
        given(meService.profile(ME))
                .willReturn(new MeProfileResult(1L, "홍길동",
                        new ProfilePhotoInfo("profile/1/key", "https://cdn/p.jpg", new PhotoPosInfo(new PhotoPosInfo.StartPos(10, 20), 100, 200)),
                        "주말마다 북한산에 오르며 사진으로 순간을 남기는 사람",
                        List.of("등산", "영화"), 2, 1));

        // when: 내 프로필 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/profile")
                .header("Authorization", BEARER));

        // then: 200 반환 + userId는 문자열로 직렬화 + 인증 유저 id로 서비스에 위임
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("1"))
                .andExpect(jsonPath("$.userName").value("홍길동"))
                .andExpect(jsonPath("$.profilePhoto.key").value("profile/1/key"))
                .andExpect(jsonPath("$.profilePhoto.photoUrl").value("https://cdn/p.jpg"))
                .andExpect(jsonPath("$.persona").value("주말마다 북한산에 오르며 사진으로 순간을 남기는 사람"))
                .andExpect(jsonPath("$.interests[0]").value("등산"))
                .andExpect(jsonPath("$.interests[1]").value("영화"))
                .andExpect(jsonPath("$.encounteredPeopleCount").value(2))
                .andExpect(jsonPath("$.encounteredFriendCount").value(1));
        then(meService).should().profile(ME);
    }

    @Test
    @DisplayName("내 프로필 조회 시 프로필 사진이 없으면 profilePhoto를 null로 직렬화한다")
    void myProfile_without_photo_serializes_null() throws Exception {
        // given: 서비스가 사진 없는 프로필을 반환
        given(meService.profile(ME))
                .willReturn(new MeProfileResult(1L, "홍길동", null, "...", List.of(), 0, 0));

        // when: 내 프로필 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/profile")
                .header("Authorization", BEARER));

        // then: 200 반환 + profilePhoto는 null, 관심사는 빈 배열
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhoto").doesNotExist())
                .andExpect(jsonPath("$.interests").isEmpty());
    }

    @Test
    @DisplayName("내 프로필 조회 시 인증 헤더가 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void myProfile_without_auth_returns_401() throws Exception {
        // when: 인증 헤더 없이 내 프로필 조회 API 호출
        var result = mockMvc.perform(get("/api/v1/me/profile"));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(meService).should(never()).profile(anyLong());
    }
}

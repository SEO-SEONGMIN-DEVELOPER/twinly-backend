package com.nidus.twinly.app.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.app.domain.AppBlockPolicy;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersion;
import com.nidus.twinly.app.domain.AppVersionPolicy;
import com.nidus.twinly.app.domain.MaintenanceState;
import com.nidus.twinly.app.dto.command.AppVersionPolicyUpdateCommand;
import com.nidus.twinly.app.dto.command.MaintenanceUpdateCommand;
import com.nidus.twinly.app.service.AppBlockPolicyService;
import com.nidus.twinly.common.security.SecurityConfig;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppAdminController.class)
@Import(SecurityConfig.class)
class AppAdminControllerUnitTest {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AppBlockPolicyService appBlockPolicyService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("차단 정책 조회 시 200과 점검 상태·플랫폼별 버전 정책을 반환한다")
    void blockPolicy_success() throws Exception {
        // given: 점검 중이고 ios 버전 정책만 설정된 상태
        given(appBlockPolicyService.current()).willReturn(new AppBlockPolicy(
                new MaintenanceState(true, "점검 중입니다", Instant.parse("2026-01-01T00:00:00Z")),
                Map.of(AppPlatform.IOS, new AppVersionPolicy(new AppVersion(1, 2, 3), "https://apps.apple.com/app"))));

        // when: 관리자 토큰으로 차단 정책 조회
        var result = mockMvc.perform(get("/admin/app/block-policy")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN));

        // then: 200 반환 + 점검 상태와 ios 정책이 담기고 미설정 플랫폼은 null
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.active").value(true))
                .andExpect(jsonPath("$.maintenance.message").value("점검 중입니다"))
                .andExpect(jsonPath("$.ios.minVersion").value("1.2.3"))
                .andExpect(jsonPath("$.ios.storeUrl").value("https://apps.apple.com/app"))
                .andExpect(jsonPath("$.android").doesNotExist());
    }

    @Test
    @DisplayName("점검 상태 변경 성공 시 200을 반환하고 요청 본문 값으로 서비스를 호출한다")
    void updateMaintenance_success() throws Exception {
        // when: 관리자 토큰으로 점검 켜기 요청
        var result = mockMvc.perform(put("/admin/app/maintenance")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"active": true, "message": "점검 중입니다", "until": "2026-01-01T00:00:00Z"}
                        """));

        // then: 200 반환 + 요청 본문 값이 그대로 커맨드에 담겨 서비스로 전달됨
        result.andExpect(status().isOk());

        ArgumentCaptor<MaintenanceUpdateCommand> captor = ArgumentCaptor.forClass(MaintenanceUpdateCommand.class);
        then(appBlockPolicyService).should().updateMaintenance(captor.capture());
        assertThat(captor.getValue().active()).isTrue();
        assertThat(captor.getValue().message()).isEqualTo("점검 중입니다");
        assertThat(captor.getValue().until()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("점검 상태 변경 시 active가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void updateMaintenance_withoutActive_returns_400() throws Exception {
        // when: 필수 필드 active를 뺀 요청
        var result = mockMvc.perform(put("/admin/app/maintenance")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"message": "점검 중입니다"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(appBlockPolicyService).should(never()).updateMaintenance(any());
    }

    @Test
    @DisplayName("버전 정책 변경 성공 시 경로의 플랫폼과 본문 값으로 서비스를 호출한다")
    void updateVersionPolicy_success() throws Exception {
        // when: ios 경로로 최소 버전·스토어 URL 변경 요청
        var result = mockMvc.perform(put("/admin/app/version-policies/{platform}", "ios")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"minVersion": "1.2.3", "storeUrl": "https://apps.apple.com/app"}
                        """));

        // then: 200 반환 + 경로 변수 플랫폼과 본문 값이 커맨드에 담김
        result.andExpect(status().isOk());

        ArgumentCaptor<AppVersionPolicyUpdateCommand> captor = ArgumentCaptor.forClass(AppVersionPolicyUpdateCommand.class);
        then(appBlockPolicyService).should().updateVersionPolicy(captor.capture());
        assertThat(captor.getValue().platform()).isEqualTo(AppPlatform.IOS);
        assertThat(captor.getValue().minVersion()).isEqualTo(new AppVersion(1, 2, 3));
        assertThat(captor.getValue().storeUrl()).isEqualTo("https://apps.apple.com/app");
    }

    @Test
    @DisplayName("버전 정책 변경 시 storeUrl이 https가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void updateVersionPolicy_withNonHttpsStoreUrl_returns_400() throws Exception {
        // when: http 스토어 URL로 변경 요청
        var result = mockMvc.perform(put("/admin/app/version-policies/{platform}", "ios")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"minVersion": "1.2.3", "storeUrl": "http://apps.apple.com/app"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(appBlockPolicyService).should(never()).updateVersionPolicy(any());
    }

    @Test
    @DisplayName("버전 정책 변경 시 minVersion이 major.minor.patch 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void updateVersionPolicy_withMalformedMinVersion_returns_400() throws Exception {
        // when: 세 자리 형식이 아닌 버전으로 변경 요청
        var result = mockMvc.perform(put("/admin/app/version-policies/{platform}", "ios")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"minVersion": "1.2", "storeUrl": "https://apps.apple.com/app"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(appBlockPolicyService).should(never()).updateVersionPolicy(any());
    }

    @Test
    @DisplayName("버전 정책 변경 시 ios·android 외 플랫폼 경로는 400을 반환하고 서비스를 호출하지 않는다")
    void updateVersionPolicy_withUnknownPlatform_returns_400() throws Exception {
        // when: 정의되지 않은 플랫폼 경로로 변경 요청
        var result = mockMvc.perform(put("/admin/app/version-policies/{platform}", "windows")
                .header(ADMIN_TOKEN_HEADER, ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"minVersion": "1.2.3", "storeUrl": "https://apps.apple.com/app"}
                        """));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(appBlockPolicyService).should(never()).updateVersionPolicy(any());
    }

    @Test
    @DisplayName("관리자 토큰이 없으면 401을 반환하고 서비스를 호출하지 않는다")
    void updateMaintenance_withoutAdminToken_returns_401() throws Exception {
        // when: 관리자 토큰 없이 점검 상태 변경 요청
        var result = mockMvc.perform(put("/admin/app/maintenance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"active": true}
                        """));

        // then: 401 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isUnauthorized());
        then(appBlockPolicyService).should(never()).updateMaintenance(any());
    }
}

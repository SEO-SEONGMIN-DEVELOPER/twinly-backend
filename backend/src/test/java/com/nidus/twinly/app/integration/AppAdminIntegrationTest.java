package com.nidus.twinly.app.integration;

import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "admin.api-token=" + AppAdminIntegrationTest.ADMIN_TOKEN)
class AppAdminIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "app-admin-integration-token";

    private static final String POLICY_PATH = "/admin/app/block-policy";
    private static final String MAINTENANCE_PATH = "/admin/app/maintenance";
    private static final String VERSION_POLICY_PATH = "/admin/app/version-policies/";

    @Autowired
    StringRedisTemplate redisTemplate;

    @AfterEach
    void clearRedis() {
        redisTemplate.delete(List.of("app:maintenance", "app:version-policy:ios", "app:version-policy:android"));
    }

    @Test
    @DisplayName("점검을 켜면 조회에 즉시 반영된다 (같은 서버는 캐시를 비운다)")
    void updateMaintenance_reflectedInPolicy() throws Exception {
        // when
        mockMvc.perform(put(MAINTENANCE_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":true,"message":"점검 중이에요.","until":"2026-09-03T09:00:00Z"}
                                """))
                .andExpect(status().isOk());

        // then
        mockMvc.perform(get(POLICY_PATH).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.active").value(true))
                .andExpect(jsonPath("$.maintenance.message").value("점검 중이에요."))
                .andExpect(jsonPath("$.maintenance.until").value("2026-09-03T18:00:00+09:00"))
                .andExpect(jsonPath("$.ios").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.android").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("message·until을 비우면 키가 null로 남는다")
    void updateMaintenance_allowsNullMessageAndUntil() throws Exception {
        mockMvc.perform(put(MAINTENANCE_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(POLICY_PATH).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.active").value(false))
                .andExpect(jsonPath("$.maintenance.message").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.maintenance.until").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("active가 없으면 400 INVALID_REQUEST")
    void updateMaintenance_requiresActive() throws Exception {
        mockMvc.perform(put(MAINTENANCE_PATH)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"점검 중이에요."}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
    }

    @Test
    @DisplayName("플랫폼별 버전 정책을 저장하면 해당 플랫폼에만 반영된다")
    void updateVersionPolicy_perPlatform() throws Exception {
        mockMvc.perform(put(VERSION_POLICY_PATH + "ios")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minVersion":"0.2.0","storeUrl":"https://apps.apple.com/kr/app/id0000000000"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(POLICY_PATH).header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ios.minVersion").value("0.2.0"))
                .andExpect(jsonPath("$.ios.storeUrl").value("https://apps.apple.com/kr/app/id0000000000"))
                .andExpect(jsonPath("$.android").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("minVersion이 major.minor.patch 형식이 아니면 400 INVALID_REQUEST")
    void updateVersionPolicy_rejectsInvalidVersion() throws Exception {
        mockMvc.perform(put(VERSION_POLICY_PATH + "android")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minVersion":"1.0","storeUrl":"https://play.google.com/store/apps/details?id=com.nidus.twinly"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
    }

    @Test
    @DisplayName("storeUrl이 https가 아니면 400 INVALID_REQUEST")
    void updateVersionPolicy_rejectsNonHttpsStoreUrl() throws Exception {
        mockMvc.perform(put(VERSION_POLICY_PATH + "android")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minVersion":"1.0.0","storeUrl":"http://play.google.com/store/apps/details?id=com.nidus.twinly"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
    }

    @Test
    @DisplayName("ios·android 외 플랫폼 경로는 400 INVALID_REQUEST")
    void updateVersionPolicy_rejectsUnknownPlatform() throws Exception {
        mockMvc.perform(put(VERSION_POLICY_PATH + "web")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minVersion":"1.0.0","storeUrl":"https://example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.name()));
    }

    @Test
    @DisplayName("관리자 토큰 없이는 호출할 수 없다")
    void requiresAdminToken() throws Exception {
        mockMvc.perform(put(MAINTENANCE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":true}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }
}

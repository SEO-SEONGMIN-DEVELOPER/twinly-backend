package com.nidus.twinly.app.integration;

import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersion;
import com.nidus.twinly.app.domain.AppVersionPolicy;
import com.nidus.twinly.app.domain.MaintenanceState;
import com.nidus.twinly.app.filter.AppBlockFilter;
import com.nidus.twinly.app.store.AppBlockPolicyStore;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 점검·필수 업데이트 필터의 HTTP 계약을 실제 필터 체인 위에서 고정한다.
 * 판정 로직 자체는 {@code AppBlockFilterUnitTest}가 담당하고, 여기서는 필터의 "위치"가 만드는 성질을 검증한다:
 * Security보다 앞이라 토큰 상태와 무관하게 끊기는지, /api/** 밖은 건드리지 않는지, 응답 형식이 우리 규약인지.
 */
@TestPropertySource(properties = "admin.api-token=" + AppBlockFilterIntegrationTest.ADMIN_TOKEN)
class AppBlockFilterIntegrationTest extends AbstractIntegrationTest {

    static final String ADMIN_TOKEN = "app-block-filter-token";

    private static final String AUTHENTICATED_PATH = "/api/v1/blocks";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String STATUS_PATH = "/api/v1/app/status";
    private static final String IOS_STORE = "https://apps.apple.com/kr/app/id0000000000";

    @Autowired
    AppBlockPolicyStore appBlockPolicyStore;

    @Autowired
    StringRedisTemplate redisTemplate;

    @AfterEach
    void clearPolicy() {
        appBlockPolicyStore.saveMaintenance(MaintenanceState.none());
        redisTemplate.delete(List.of("app:maintenance", "app:version-policy:ios", "app:version-policy:android"));
    }

    @Nested
    @DisplayName("점검 ON")
    class Maintenance {

        @Test
        @DisplayName("위조 토큰으로 인증 endpoint를 쳐도 401이 아니라 503 MAINTENANCE (필터가 Security보다 앞)")
        void forgedToken_returns503NotUnauthorized() throws Exception {
            appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, null, null));

            mockMvc.perform(get(AUTHENTICATED_PATH)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(ErrorCode.MAINTENANCE.name()));
        }

        @Test
        @DisplayName("토큰 없이 인증 endpoint를 쳐도 401이 아니라 503 MAINTENANCE")
        void noToken_returns503NotUnauthorized() throws Exception {
            appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, null, null));

            mockMvc.perform(get(AUTHENTICATED_PATH))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(ErrorCode.MAINTENANCE.name()));
        }

        @Test
        @DisplayName("유효한 토큰이라도 503 MAINTENANCE")
        void validToken_returns503() throws Exception {
            User user = saveUser();
            appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, null, null));

            mockMvc.perform(get(AUTHENTICATED_PATH)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user.getId())))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(ErrorCode.MAINTENANCE.name()));
        }

        @Test
        @DisplayName("토큰 refresh도 503 MAINTENANCE (401·INVALID_REFRESH_TOKEN으로 바꿔 내리면 앱이 로그아웃된다)")
        void refresh_returns503() throws Exception {
            appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, null, null));

            mockMvc.perform(post(REFRESH_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"expired-or-whatever"}
                                    """))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(ErrorCode.MAINTENANCE.name()));
        }

        @Test
        @DisplayName("버전 미달 헤더를 보내도 426이 아니라 503 (점검 판정이 먼저)")
        void outdatedVersion_returns503NotUpgradeRequired() throws Exception {
            appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, null, null));
            appBlockPolicyStore.saveVersionPolicy(AppPlatform.IOS, new AppVersionPolicy(AppVersion.from("0.2.0"), IOS_STORE));

            mockMvc.perform(get(STATUS_PATH)
                            .header(AppBlockFilter.PLATFORM_HEADER, "ios")
                            .header(AppBlockFilter.VERSION_HEADER, "0.1.0"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(ErrorCode.MAINTENANCE.name()));
        }

        @Test
        @DisplayName("응답은 Boot 기본 오류 형식(status/error/path)이 아니라 code가 있는 우리 형식이고 application/json이다")
        void body_isOurErrorFormat() throws Exception {
            Instant until = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
            appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, "곧 돌아올게요.", until));

            mockMvc.perform(get(STATUS_PATH))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andExpect(jsonPath("$.code").value(ErrorCode.MAINTENANCE.name()))
                    .andExpect(jsonPath("$.message").value("곧 돌아올게요."))
                    .andExpect(jsonPath("$.until").isString())
                    .andExpect(jsonPath("$.status").doesNotExist())
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(jsonPath("$.path").doesNotExist());
        }

        @Test
        @DisplayName("/admin/** 은 점검 중에도 통과해야 점검을 끌 수 있다")
        void adminPath_passes() throws Exception {
            appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, null, null));

            mockMvc.perform(get("/admin/app/block-policy").header("X-Admin-Token", ADMIN_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maintenance.active").value(true));
        }

        @Test
        @DisplayName("/internal/** 은 점검 중에도 통과한다 (경로가 없어 404 = 필터를 지나 라우팅까지 갔다는 증거)")
        void internalPath_passes() throws Exception {
            appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, null, null));

            mockMvc.perform(get("/internal/v1/ping"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));
        }
    }

    @Nested
    @DisplayName("점검 OFF, 버전 정책 있음")
    class VersionPolicy {

        @Test
        @DisplayName("버전 미달이면 426 APP_UPDATE_REQUIRED, storeUrl·minVersion non-null, application/json")
        void outdated_returns426() throws Exception {
            appBlockPolicyStore.saveVersionPolicy(AppPlatform.IOS, new AppVersionPolicy(AppVersion.from("0.2.0"), IOS_STORE));

            mockMvc.perform(get(STATUS_PATH)
                            .header(AppBlockFilter.PLATFORM_HEADER, "ios")
                            .header(AppBlockFilter.VERSION_HEADER, "0.1.2"))
                    .andExpect(status().isUpgradeRequired())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(jsonPath("$.code").value(ErrorCode.APP_UPDATE_REQUIRED.name()))
                    .andExpect(jsonPath("$.message").isString())
                    .andExpect(jsonPath("$.storeUrl").value(IOS_STORE))
                    .andExpect(jsonPath("$.minVersion").value("0.2.0"))
                    .andExpect(jsonPath("$.status").doesNotExist());
        }

        @Test
        @DisplayName("유효한 토큰이라도 버전 미달이면 426 (인증보다 앞에서 끊는다)")
        void outdated_withValidToken_returns426() throws Exception {
            User user = saveUser();
            appBlockPolicyStore.saveVersionPolicy(AppPlatform.IOS, new AppVersionPolicy(AppVersion.from("0.2.0"), IOS_STORE));

            mockMvc.perform(get(AUTHENTICATED_PATH)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user.getId()))
                            .header(AppBlockFilter.PLATFORM_HEADER, "ios")
                            .header(AppBlockFilter.VERSION_HEADER, "0.1.2"))
                    .andExpect(status().isUpgradeRequired())
                    .andExpect(jsonPath("$.code").value(ErrorCode.APP_UPDATE_REQUIRED.name()));
        }

        @Test
        @DisplayName("최소 버전 이상이면 통과한다")
        void upToDate_passes() throws Exception {
            appBlockPolicyStore.saveVersionPolicy(AppPlatform.IOS, new AppVersionPolicy(AppVersion.from("0.2.0"), IOS_STORE));

            mockMvc.perform(get(STATUS_PATH)
                            .header(AppBlockFilter.PLATFORM_HEADER, "ios")
                            .header(AppBlockFilter.VERSION_HEADER, "0.2.0"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("헤더가 없으면 통과한다")
        void noHeaders_pass() throws Exception {
            appBlockPolicyStore.saveVersionPolicy(AppPlatform.IOS, new AppVersionPolicy(AppVersion.from("0.2.0"), IOS_STORE));

            mockMvc.perform(get(STATUS_PATH))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("X-App-Platform이 ios·android 외 값이면 통과한다")
        void unknownPlatform_passes() throws Exception {
            appBlockPolicyStore.saveVersionPolicy(AppPlatform.IOS, new AppVersionPolicy(AppVersion.from("0.2.0"), IOS_STORE));

            mockMvc.perform(get(STATUS_PATH)
                            .header(AppBlockFilter.PLATFORM_HEADER, "web")
                            .header(AppBlockFilter.VERSION_HEADER, "0.0.1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("정책이 없는 플랫폼은 통과한다 (iOS 정책만 있을 때 구버전 Android)")
        void platformWithoutPolicy_passes() throws Exception {
            appBlockPolicyStore.saveVersionPolicy(AppPlatform.IOS, new AppVersionPolicy(AppVersion.from("0.2.0"), IOS_STORE));

            mockMvc.perform(get(STATUS_PATH)
                            .header(AppBlockFilter.PLATFORM_HEADER, "android")
                            .header(AppBlockFilter.VERSION_HEADER, "0.0.1"))
                    .andExpect(status().isOk());
        }
    }
}

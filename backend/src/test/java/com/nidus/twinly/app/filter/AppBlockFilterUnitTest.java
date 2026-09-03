package com.nidus.twinly.app.filter;

import com.nidus.twinly.app.domain.AppBlockPolicy;
import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.domain.AppVersion;
import com.nidus.twinly.app.domain.AppVersionPolicy;
import com.nidus.twinly.app.domain.MaintenanceState;
import com.nidus.twinly.app.store.AppBlockPolicyStore;
import com.nidus.twinly.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppBlockFilterUnitTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final String IOS_STORE = "https://apps.apple.com/kr/app/id0000000000";
    private static final String ANDROID_STORE = "https://play.google.com/store/apps/details?id=com.nidus.twinly";

    @Mock
    AppBlockPolicyStore store;

    JsonMapper jsonMapper = JsonMapper.builder().build();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/app/status");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();
    AppBlockFilter filter;

    @BeforeEach
    void setUp() {
        request.setRequestURI("/api/v1/app/status");
        filter = new AppBlockFilter(store, jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("점검 중이면 헤더와 무관하게 503 MAINTENANCE를 내리고 다음 필터로 넘기지 않는다")
    void maintenance_blocksRegardlessOfHeaders() throws Exception {
        // given
        Instant until = Instant.parse("2026-09-03T00:10:00Z");
        given(store.current()).willReturn(policy(new MaintenanceState(true, "곧 돌아올게요.", until), Map.of()));

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("600");
        JsonNode body = body();
        assertThat(body.get("code").asString()).isEqualTo(ErrorCode.MAINTENANCE.name());
        assertThat(body.get("message").asString()).isEqualTo("곧 돌아올게요.");
        assertThat(body.get("until").asString()).isEqualTo("2026-09-03T00:10:00Z");
        assertThat(filterChain.getRequest()).isNull();
    }

    @Test
    @DisplayName("점검 문구와 예정 시각을 비우면 기본 문구를 넣고 until 키는 null로 남기며 Retry-After는 생략한다")
    void maintenance_fillsDefaultsWhenEmpty() throws Exception {
        // given
        given(store.current()).willReturn(policy(new MaintenanceState(true, null, null), Map.of()));

        // when
        filter.doFilter(request, response, filterChain);

        // then
        JsonNode body = body();
        assertThat(body.get("message").asString()).isEqualTo(ErrorCode.MAINTENANCE.getDefaultMessage());
        assertThat(body.has("until")).isTrue();
        assertThat(body.get("until").isNull()).isTrue();
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }

    @Test
    @DisplayName("예정 시각이 이미 지났으면 Retry-After를 붙이지 않는다")
    void maintenance_omitsRetryAfterWhenUntilPassed() throws Exception {
        // given
        given(store.current()).willReturn(policy(new MaintenanceState(true, null, NOW.minusSeconds(1)), Map.of()));

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }

    @Test
    @DisplayName("점검 중이면 버전 미달이어도 426이 아니라 503이다 (점검 판정이 먼저)")
    void maintenance_takesPrecedenceOverVersion() throws Exception {
        // given
        given(store.current()).willReturn(policy(new MaintenanceState(true, null, null), iosMin("0.2.0")));
        request.addHeader(AppBlockFilter.PLATFORM_HEADER, "ios");
        request.addHeader(AppBlockFilter.VERSION_HEADER, "0.1.0");

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("버전이 최소 버전보다 낮으면 426 APP_UPDATE_REQUIRED와 플랫폼의 storeUrl·minVersion을 내린다")
    void outdated_returns426WithStoreUrl() throws Exception {
        // given
        given(store.current()).willReturn(policy(MaintenanceState.none(), iosMin("0.2.0")));
        request.addHeader(AppBlockFilter.PLATFORM_HEADER, "ios");
        request.addHeader(AppBlockFilter.VERSION_HEADER, "0.1.9");

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        JsonNode body = body();
        assertThat(body.get("code").asString()).isEqualTo(ErrorCode.APP_UPDATE_REQUIRED.name());
        assertThat(body.get("message").asString()).isEqualTo(ErrorCode.APP_UPDATE_REQUIRED.getDefaultMessage());
        assertThat(body.get("storeUrl").asString()).isEqualTo(IOS_STORE);
        assertThat(body.get("minVersion").asString()).isEqualTo("0.2.0");
        assertThat(filterChain.getRequest()).isNull();
    }

    @ParameterizedTest(name = "버전 {0}")
    @DisplayName("최소 버전과 같거나 높으면 통과한다")
    @ValueSource(strings = {"0.2.0", "0.2.1", "1.0.0"})
    void upToDate_passes(String version) throws Exception {
        // given
        given(store.current()).willReturn(policy(MaintenanceState.none(), iosMin("0.2.0")));
        request.addHeader(AppBlockFilter.PLATFORM_HEADER, "ios");
        request.addHeader(AppBlockFilter.VERSION_HEADER, version);

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("플랫폼별로 따로 판정한다: iOS 정책만 있으면 구버전 Android는 통과한다")
    void perPlatform_androidPassesWhenOnlyIosPolicy() throws Exception {
        // given
        given(store.current()).willReturn(policy(MaintenanceState.none(), iosMin("0.2.0")));
        request.addHeader(AppBlockFilter.PLATFORM_HEADER, "android");
        request.addHeader(AppBlockFilter.VERSION_HEADER, "0.0.1");

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("플랫폼별로 따로 판정한다: Android는 Android 정책의 storeUrl을 받는다")
    void perPlatform_androidGetsAndroidStoreUrl() throws Exception {
        // given
        given(store.current()).willReturn(policy(MaintenanceState.none(), Map.of(
                AppPlatform.IOS, new AppVersionPolicy(AppVersion.from("0.2.0"), IOS_STORE),
                AppPlatform.ANDROID, new AppVersionPolicy(AppVersion.from("0.1.5"), ANDROID_STORE))));
        request.addHeader(AppBlockFilter.PLATFORM_HEADER, "android");
        request.addHeader(AppBlockFilter.VERSION_HEADER, "0.1.4");

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(body().get("storeUrl").asString()).isEqualTo(ANDROID_STORE);
        assertThat(body().get("minVersion").asString()).isEqualTo("0.1.5");
    }

    @ParameterizedTest(name = "platform={0}, version={1}")
    @DisplayName("헤더가 없거나 해석할 수 없으면 버전 판정 없이 통과한다")
    @CsvSource(value = {
            "null, null",
            "ios, null",
            "null, 0.1.0",
            "web, 0.1.0",
            "ios, 0.1",
            "ios, v0.1.0"
    }, nullValues = "null")
    void unparsableHeaders_pass(String platform, String version) throws Exception {
        // given
        given(store.current()).willReturn(policy(MaintenanceState.none(), iosMin("0.2.0")));
        if (platform != null) {
            request.addHeader(AppBlockFilter.PLATFORM_HEADER, platform);
        }
        if (version != null) {
            request.addHeader(AppBlockFilter.VERSION_HEADER, version);
        }

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("/api/** 밖의 경로는 점검 중이어도 정책을 읽지 않고 통과한다 (admin·internal·webhook·docs)")
    @ValueSource(strings = {"/admin/app/maintenance", "/internal/v1/users", "/webhook/v1/revenuecat", "/docs/openapi", "/ws/v1"})
    void nonApiPaths_bypass(String path) throws Exception {
        // given
        MockHttpServletRequest outside = new MockHttpServletRequest("GET", path);
        outside.setRequestURI(path);

        // when
        filter.doFilter(outside, response, filterChain);

        // then
        assertThat(filterChain.getRequest()).isSameAs(outside);
        then(store).should(never()).current();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("/api/** 안이면 버전 경로와 무관하게 판정한다")
    @ValueSource(strings = {"/api/v1/auth/refresh", "/api/v1/chat/rooms/1", "/api/v2/anything"})
    void apiPaths_areFiltered(String path) throws Exception {
        // given
        given(store.current()).willReturn(policy(new MaintenanceState(true, null, null), Map.of()));
        MockHttpServletRequest inside = new MockHttpServletRequest("POST", path);
        inside.setRequestURI(path);

        // when
        filter.doFilter(inside, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(503);
    }

    private JsonNode body() {
        return jsonMapper.readTree(response.getContentAsByteArray());
    }

    private static AppBlockPolicy policy(MaintenanceState maintenance, Map<AppPlatform, AppVersionPolicy> versionPolicies) {
        return new AppBlockPolicy(maintenance, versionPolicies);
    }

    private static Map<AppPlatform, AppVersionPolicy> iosMin(String minVersion) {
        return Map.of(AppPlatform.IOS, new AppVersionPolicy(AppVersion.from(minVersion), IOS_STORE));
    }
}

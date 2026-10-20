package com.nidus.twinly.common.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CallerTaggingObservationConventionUnitTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("요청 경로로 호출 주체(앱·외부 서비스·AI 서버·운영자·그 외)를 나눈다")
    @CsvSource({
            "/api/v1/main, app",
            "/api/v2/activities/2026-10-20, app",
            "/ws/v1/, app",
            "/api/v1/auth/onboarding/identity/return, external",
            "/api/v1/auth/onboarding/identity/close, external",
            "/webhook/v1/revenue-cat, external",
            "/internal/v1/users, ai",
            "/admin/app/maintenance, admin",
            "/, other",
            "/robots.txt, other",
            "/_next/static, other",
            "/api, other"
    })
    void resolveCaller(String path, String expected) {
        assertThat(CallerTaggingObservationConvention.resolveCaller(path)).isEqualTo(expected);
    }

    @Test
    @DisplayName("요청 정보가 없으면 그 외로 분류한다")
    void resolveCaller_without_path_is_other() {
        assertThat(CallerTaggingObservationConvention.resolveCaller(null)).isEqualTo("other");
    }
}

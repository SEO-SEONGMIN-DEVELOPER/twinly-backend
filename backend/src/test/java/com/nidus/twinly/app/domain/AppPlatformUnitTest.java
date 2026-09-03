package com.nidus.twinly.app.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AppPlatformUnitTest {

    @ParameterizedTest(name = "헤더 \"{0}\" → {1}")
    @DisplayName("ios·android 헤더 값을 플랫폼으로 해석한다 (대소문자·공백 허용)")
    @CsvSource({
            "ios, IOS",
            "android, ANDROID",
            "IOS, IOS",
            "' android ', ANDROID"
    })
    void fromHeader_resolvesKnownPlatforms(String header, AppPlatform expected) {
        assertThat(AppPlatform.fromHeader(header)).contains(expected);
    }

    @ParameterizedTest(name = "헤더 \"{0}\" → 빈 값")
    @DisplayName("헤더가 없거나 ios·android 외의 값이면 빈 값으로 돌려 필터가 통과시키게 한다")
    @NullAndEmptySource
    @ValueSource(strings = {"web", "iphone", "android-tv", " "})
    void fromHeader_returnsEmptyForUnknown(String header) {
        assertThat(AppPlatform.fromHeader(header)).isEmpty();
    }

    @Test
    @DisplayName("두 플랫폼만 존재한다")
    void onlyTwoPlatforms() {
        assertThat(AppPlatform.values()).containsExactly(AppPlatform.IOS, AppPlatform.ANDROID);
    }
}

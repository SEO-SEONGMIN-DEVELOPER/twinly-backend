package com.nidus.twinly.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestVerificationPropertiesTest {

    @Test
    @DisplayName("설정이 없으면 바인딩에 실패하지 않고 어떤 연락처도 매칭되지 않는다")
    void 설정이_없으면_매칭되지_않는다() {
        TestVerificationProperties properties = bind(Map.of());

        assertThat(properties.code()).isNull();
        assertThat(properties.matches("01000001234")).isFalse();
        assertThat(properties.matches("test1@skku.edu")).isFalse();
    }

    @Test
    @DisplayName("설정이 있으면 접두사가 일치하는 연락처만 매칭된다")
    void 접두사가_일치하는_연락처만_매칭된다() {
        TestVerificationProperties properties = bind(Map.of(
                "verification.test.code", "000000",
                "verification.test.phone-prefix", "0100000",
                "verification.test.email-prefix", "test"
        ));

        assertThat(properties.matches("01000001234")).isTrue();
        assertThat(properties.matches("test1@skku.edu")).isTrue();
        assertThat(properties.matches("01098765432")).isFalse();
        assertThat(properties.matches("real@skku.edu")).isFalse();
    }

    private TestVerificationProperties bind(Map<String, Object> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bindOrCreate("verification.test", TestVerificationProperties.class);
    }
}

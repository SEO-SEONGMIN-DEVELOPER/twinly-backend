package com.nidus.twinly.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredPropertyTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "${JWT_SECRET_KEY}"})
    @DisplayName("값이 없거나 비어 있거나 해석되지 않은 플레이스홀더면 설정 키를 담아 예외가 발생한다")
    void 설정되지_않은_값이면_예외가_발생한다(String value) {
        assertThatThrownBy(() -> RequiredProperty.require("jwt.secret-key", value))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("jwt.secret-key 가 설정되지 않았습니다.");
    }

    @Test
    @DisplayName("값이 설정되어 있으면 예외가 발생하지 않는다")
    void 설정된_값이면_통과한다() {
        assertThatCode(() -> RequiredProperty.require("jwt.secret-key", "abc"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("notPositiveDurations")
    @DisplayName("기간이 없거나 0 이하면 예외가 발생한다")
    void 기간이_0_이하면_예외가_발생한다(Duration value) {
        assertThatThrownBy(() -> RequiredProperty.requirePositive("revenue-cat.read-timeout", value))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("revenue-cat.read-timeout 는 0보다 커야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("정수가 0 이하면 예외가 발생한다")
    void 정수가_0_이하면_예외가_발생한다(int value) {
        assertThatThrownBy(() -> RequiredProperty.requirePositive("withdrawn-user-deletion.chunk-size", value))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("withdrawn-user-deletion.chunk-size 는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("기간과 정수가 양수면 예외가 발생하지 않는다")
    void 양수면_통과한다() {
        assertThatCode(() -> RequiredProperty.requirePositive("revenue-cat.read-timeout", Duration.ofMillis(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> RequiredProperty.requirePositive("withdrawn-user-deletion.chunk-size", 1))
                .doesNotThrowAnyException();
    }

    private static Stream<Duration> notPositiveDurations() {
        return Stream.of(null, Duration.ZERO, Duration.ofSeconds(-1));
    }
}

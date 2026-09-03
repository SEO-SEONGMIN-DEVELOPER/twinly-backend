package com.nidus.twinly.app.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AppVersionUnitTest {

    @ParameterizedTest(name = "\"{0}\" → {1}.{2}.{3}")
    @DisplayName("major.minor.patch 형식을 세 자리 숫자로 해석한다")
    @CsvSource({
            "0.1.2, 0, 1, 2",
            "1.0.0, 1, 0, 0",
            "10.20.30, 10, 20, 30",
            "' 0.2.0 ', 0, 2, 0"
    })
    void parse_readsThreeNumbers(String value, int major, int minor, int patch) {
        assertThat(AppVersion.parse(value)).contains(new AppVersion(major, minor, patch));
    }

    @ParameterizedTest(name = "\"{0}\" → 빈 값")
    @DisplayName("세 자리 숫자 형식이 아니면 빈 값으로 돌려 필터가 통과시키게 한다")
    @NullAndEmptySource
    @ValueSource(strings = {"1.0", "1", "1.0.0.0", "v1.0.0", "1.0.0-beta", "1.0.0+42", "a.b.c", "1..0", "-1.0.0"})
    void parse_returnsEmptyForInvalid(String value) {
        assertThat(AppVersion.parse(value)).isEmpty();
    }

    @ParameterizedTest(name = "{0} < {1}")
    @DisplayName("major → minor → patch 순으로 숫자 비교한다 (문자열 비교가 아니다)")
    @CsvSource({
            "0.1.2, 0.2.0",
            "0.9.9, 1.0.0",
            "1.0.9, 1.0.10",
            "1.9.0, 1.10.0",
            "9.0.0, 10.0.0"
    })
    void isLowerThan_comparesNumerically(String lower, String higher) {
        AppVersion low = AppVersion.parse(lower).orElseThrow();
        AppVersion high = AppVersion.parse(higher).orElseThrow();

        assertThat(low.isLowerThan(high)).isTrue();
        assertThat(high.isLowerThan(low)).isFalse();
    }

    @Test
    @DisplayName("같은 버전은 서로 낮지 않다 (최소 버전과 같으면 통과)")
    void isLowerThan_falseForEqual() {
        AppVersion version = AppVersion.parse("0.2.0").orElseThrow();

        assertThat(version.isLowerThan(new AppVersion(0, 2, 0))).isFalse();
    }

    @Test
    @DisplayName("문자열로 되돌리면 응답의 minVersion 형식과 같다")
    void toString_isDotted() {
        assertThat(new AppVersion(0, 2, 0).toString()).isEqualTo("0.2.0");
    }
}

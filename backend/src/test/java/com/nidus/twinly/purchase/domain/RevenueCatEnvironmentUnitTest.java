package com.nidus.twinly.purchase.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RevenueCatEnvironmentUnitTest {

    @ParameterizedTest(name = "{0} 서버, {1} 결제 → {2}")
    @CsvSource({
            "PRODUCTION, PRODUCTION, true",
            "PRODUCTION, SANDBOX, true",
            "SANDBOX, SANDBOX, true",
            "SANDBOX, PRODUCTION, false"
    })
    @DisplayName("prod 서버는 모든 결제를, stage 서버는 샌드박스 결제만 인정한다")
    void accepts(RevenueCatEnvironment server, RevenueCatEnvironment purchase, boolean expected) {
        assertThat(server.accepts(purchase)).isEqualTo(expected);
    }
}

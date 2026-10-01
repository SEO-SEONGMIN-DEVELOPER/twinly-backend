package com.nidus.twinly.purchase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoolPropertiesTest {

    @Test
    @DisplayName("고정 풀 번호가 없으면 null 로 읽혀 카운터 기준 배정을 그대로 쓴다")
    void missing_fixed_number_is_null() {
        // when: prod 외 환경처럼 pool 설정이 아예 없음
        PoolProperties properties = bind(Map.of());

        // then: 기동은 되고 고정 번호는 없다
        assertThat(properties.fixedNumber()).isNull();
    }

    @Test
    @DisplayName("고정 풀 번호를 적으면 그 번호로 읽힌다")
    void fixed_number_is_read() {
        // when: application-prod.yaml 과 같은 값
        PoolProperties properties = bind(Map.of("pool.fixed-number", "12"));

        // then
        assertThat(properties.fixedNumber()).isEqualTo(12);
    }

    @Test
    @DisplayName("고정 풀 번호가 0 이하이면 기동을 실패시킨다")
    void non_positive_fixed_number_fails() {
        // when & then: 존재할 수 없는 풀에 유저를 넣지 않도록 서버가 뜨지 않게 한다
        assertThatThrownBy(() -> bind(Map.of("pool.fixed-number", "0")))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pool.fixed-number");
    }

    private PoolProperties bind(Map<String, Object> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bindOrCreate("pool", PoolProperties.class);
    }
}

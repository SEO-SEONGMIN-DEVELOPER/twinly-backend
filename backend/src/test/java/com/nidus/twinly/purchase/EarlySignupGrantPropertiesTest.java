package com.nidus.twinly.purchase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EarlySignupGrantPropertiesTest {

    @Test
    @DisplayName("종료 시각을 한국 시간 오프셋과 함께 적으면 그 순간의 UTC 시각으로 읽힌다")
    void ends_at_is_read_with_kst_offset() {
        // given: application.yaml 과 같은 형식의 값 (올해가 끝나는 한국 시간 자정)
        Map<String, Object> source = Map.of("early-signup-grant.ends-at", "2027-01-01T00:00:00+09:00");

        // when: 스프링 부트와 같은 방식으로 바인딩
        EarlySignupGrantProperties properties = bind(source);

        // then: 오프셋이 무시돼 9시간 어긋나지 않고 UTC 12월 31일 15시가 된다
        assertThat(properties.endsAt()).isEqualTo(Instant.parse("2026-12-31T15:00:00Z"));
    }

    @Test
    @DisplayName("종료 시각이 없으면 기동을 실패시킨다")
    void missing_ends_at_fails() {
        // when & then: 조용히 넘어가면 언제까지 줄지 모르는 권한을 배정하게 되므로 서버가 뜨지 않게 한다
        assertThatThrownBy(() -> bind(Map.of()))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("early-signup-grant.ends-at");
    }

    private EarlySignupGrantProperties bind(Map<String, Object> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bindOrCreate("early-signup-grant", EarlySignupGrantProperties.class);
    }
}

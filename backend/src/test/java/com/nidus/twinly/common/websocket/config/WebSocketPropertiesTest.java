package com.nidus.twinly.common.websocket.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketPropertiesTest {

    @ParameterizedTest
    @ValueSource(ints = {60, 61})
    @DisplayName("heartbeat 간격이 ALB idle timeout 이상이면 기동에 실패한다")
    void heartbeat_간격이_ALB_idle_timeout_이상이면_실패한다(int seconds) {
        assertThatThrownBy(() -> new WebSocketProperties(Duration.ofSeconds(seconds)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("websocket.heartbeat-interval 는 ALB idle timeout(60초)보다 짧아야 합니다.");
    }

    @Test
    @DisplayName("heartbeat 간격이 ALB idle timeout 보다 짧으면 통과한다")
    void heartbeat_간격이_ALB_idle_timeout_보다_짧으면_통과한다() {
        assertThatCode(() -> new WebSocketProperties(Duration.ofSeconds(59)))
                .doesNotThrowAnyException();
    }
}

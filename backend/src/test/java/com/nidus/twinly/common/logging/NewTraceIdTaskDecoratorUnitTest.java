package com.nidus.twinly.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewTraceIdTaskDecoratorUnitTest {

    private final NewTraceIdTaskDecorator decorator = new NewTraceIdTaskDecorator();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("실행할 때마다 새 traceId 를 발급한다")
    void issues_new_trace_id_per_execution() {
        // given: 스케줄 등록 시점에 한 번만 감싸진 작업
        List<String> traceIds = new ArrayList<>();
        Runnable task = decorator.decorate(() -> traceIds.add(MDC.get(TraceContext.TRACE_ID)));

        // when: 주기적으로 여러 번 실행된다
        task.run();
        task.run();

        // then: 감쌀 때 발급하면 모든 실행이 같은 id 를 갖게 된다
        assertThat(traceIds).doesNotContainNull();
        assertThat(traceIds.get(0)).isNotEqualTo(traceIds.get(1));
    }

    @Test
    @DisplayName("실행이 끝나면 traceId 를 지운다")
    void clears_trace_id_after_execution() {
        // given
        Runnable task = decorator.decorate(() -> {
        });

        // when
        task.run();

        // then: 스케줄러도 스레드를 재사용한다
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
    }
}

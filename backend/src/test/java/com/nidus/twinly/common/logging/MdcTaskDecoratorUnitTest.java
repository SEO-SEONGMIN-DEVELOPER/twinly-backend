package com.nidus.twinly.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorUnitTest {

    private static final String TRACE_ID = "traceId";

    private final MdcTaskDecorator mdcTaskDecorator = new MdcTaskDecorator();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("제출 시점의 MDC 가 실행 스레드로 전달된다")
    void carries_context_to_worker_thread() throws Exception {
        // given: 요청 스레드에 traceId 가 있는 상태에서 작업을 감싼다
        MDC.put(TRACE_ID, "a3f9c210");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable task = mdcTaskDecorator.decorate(() -> seen.set(MDC.get(TRACE_ID)));

        // when: 다른 스레드에서 실행한다
        runAll(task);

        // then: 비동기 로그도 원인 요청과 같은 traceId 로 묶인다
        assertThat(seen.get()).isEqualTo("a3f9c210");
    }

    @Test
    @DisplayName("실행이 끝나면 실행 스레드에 MDC 가 남지 않는다")
    void does_not_leak_to_reused_thread() throws Exception {
        // given: traceId 를 가진 작업과, traceId 없이 제출된 작업
        MDC.put(TRACE_ID, "a3f9c210");
        Runnable first = mdcTaskDecorator.decorate(() -> {
        });

        MDC.clear();
        AtomicReference<String> seen = new AtomicReference<>("남아있음");
        Runnable second = mdcTaskDecorator.decorate(() -> seen.set(MDC.get(TRACE_ID)));

        // when: 같은 스레드에서 이어서 실행한다
        runAll(first, second);

        // then: 스레드 풀은 스레드를 재사용하므로, 남으면 다음 작업이 남의 traceId 를 물고 간다
        assertThat(seen.get()).isNull();
    }

    private void runAll(Runnable... tasks) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (Runnable task : tasks) {
                executor.submit(task).get();
            }
        } finally {
            executor.shutdown();
        }
    }
}

package com.nidus.twinly.common.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigUnitTest {

    private static final String TRACE_ID = "traceId";

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    static Stream<Named<Method>> executors() {
        return Arrays.stream(AsyncConfig.class.getDeclaredMethods())
                .filter(method -> TaskExecutor.class.isAssignableFrom(method.getReturnType()))
                .map(method -> Named.of(method.getName(), method));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("executors")
    @DisplayName("모든 비동기 Executor 는 MDC 를 실행 스레드로 옮긴다")
    void every_executor_carries_mdc(Method executorMethod) throws Exception {
        // given: 요청 스레드에 traceId 가 있는 상태
        MDC.put(TRACE_ID, "a3f9c210");
        ThreadPoolTaskExecutor executor = executor(executorMethod);

        // when: 해당 Executor 로 작업을 넘긴다
        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> seen.complete(MDC.get(TRACE_ID)));

        // then: Executor 를 새로 추가하면서 데코레이터를 빠뜨리면 여기서 잡힌다
        assertThat(seen.get()).isEqualTo("a3f9c210");

        executor.shutdown();
    }

    private ThreadPoolTaskExecutor executor(Method executorMethod) throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) executorMethod.invoke(asyncConfig);
        executor.initialize();

        return executor;
    }
}

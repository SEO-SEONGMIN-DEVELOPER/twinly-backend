package com.nidus.twinly.common.scheduling;

import com.nidus.twinly.common.logging.TraceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigUnitTest {

    @Test
    @DisplayName("스케줄 작업은 실행마다 traceId 를 갖는다")
    void scheduled_task_has_trace_id() throws Exception {
        // given: 실제 설정으로 만든 스케줄러
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) new SchedulingConfig().taskScheduler();

        // when: 작업을 실행한다
        CompletableFuture<String> seen = new CompletableFuture<>();
        scheduler.execute(() -> seen.complete(MDC.get(TraceContext.TRACE_ID)));

        // then: 원인 요청이 없으므로 스케줄러가 직접 발급해야 한다
        assertThat(seen.get()).isNotNull();

        scheduler.shutdown();
    }
}

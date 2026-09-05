package com.nidus.twinly.common.async;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.logging.MdcTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final String METHOD = "method";

    private static final TaskDecorator MDC_TASK_DECORATOR = new MdcTaskDecorator();

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> ErrorLog.error(log, null, null, ex)
                .addKeyValue(METHOD, method.getDeclaringClass().getSimpleName() + "." + method.getName())
                .log("비동기 작업 처리에 실패했습니다");
    }

    @Bean(defaultCandidate = false)
    public TaskExecutor pushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setTaskDecorator(MDC_TASK_DECORATOR);
        executor.setThreadNamePrefix("push-");
        executor.setRejectedExecutionHandler((rejected, threadPoolExecutor) ->
                log.warn("푸시 작업 큐가 가득 차 발송을 건너뜁니다. queued={}", threadPoolExecutor.getQueue().size()));

        return executor;
    }

    @Bean(defaultCandidate = false)
    public TaskExecutor purchaseSyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setTaskDecorator(MDC_TASK_DECORATOR);
        executor.setThreadNamePrefix("purchase-sync-");
        executor.setRejectedExecutionHandler((rejected, threadPoolExecutor) ->
                log.warn("구매 상태 동기화 큐가 가득 차 동기화를 건너뜁니다. queued={}", threadPoolExecutor.getQueue().size()));

        return executor;
    }

    @Bean(defaultCandidate = false)
    public TaskExecutor twinViewTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setTaskDecorator(MDC_TASK_DECORATOR);
        executor.setThreadNamePrefix("twin-view-");
        executor.setRejectedExecutionHandler((rejected, threadPoolExecutor) ->
                log.warn("트윈 열람 기록 큐가 가득 차 기록을 건너뜁니다. queued={}", threadPoolExecutor.getQueue().size()));

        return executor;
    }

    @Bean(defaultCandidate = false)
    public TaskExecutor simulationPreloadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setTaskDecorator(MDC_TASK_DECORATOR);
        executor.setThreadNamePrefix("simulation-preload-");
        executor.setRejectedExecutionHandler((rejected, threadPoolExecutor) ->
                log.warn("시뮬레이션 선생성 요청 큐가 가득 차 요청을 건너뜁니다. queued={}", threadPoolExecutor.getQueue().size()));

        return executor;
    }
}

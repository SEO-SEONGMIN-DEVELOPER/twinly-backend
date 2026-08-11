package com.nidus.twinly.common.async;

import com.nidus.twinly.common.logging.ErrorLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final String METHOD = "method";

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
        executor.setThreadNamePrefix("push-");
        executor.setRejectedExecutionHandler((rejected, threadPoolExecutor) ->
                log.warn("푸시 작업 큐가 가득 차 발송을 건너뜁니다. queued={}", threadPoolExecutor.getQueue().size()));

        return executor;
    }
}

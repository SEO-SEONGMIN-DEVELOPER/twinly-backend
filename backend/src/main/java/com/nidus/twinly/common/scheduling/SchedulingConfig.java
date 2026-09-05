package com.nidus.twinly.common.scheduling;

import com.nidus.twinly.common.logging.ErrorLog;
import com.nidus.twinly.common.logging.NewTraceIdTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

@Slf4j
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    private static final TaskDecorator NEW_TRACE_ID_TASK_DECORATOR = new NewTraceIdTaskDecorator();

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(1);
        scheduler.setTaskDecorator(NEW_TRACE_ID_TASK_DECORATOR);
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setErrorHandler(e -> ErrorLog.error(log, null, null, e)
                .log("스케줄 작업 처리에 실패했습니다"));
        scheduler.initialize();

        return scheduler;
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}

package com.testhub.uiexec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated pool for deterministic {@code STEP} task execution. Keeping it separate from the
 * scheduler / watchdog avoids a long step run starving timeout scans.
 */
@Configuration
public class StepExecutionConfig {

    @Bean(name = "stepTaskExecutor")
    public ThreadPoolTaskExecutor stepTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("step-task-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}

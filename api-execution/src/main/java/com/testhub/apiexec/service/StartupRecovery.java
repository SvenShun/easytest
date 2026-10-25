package com.testhub.apiexec.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Marks tasks that were pending/running on the previous process as FAILED. The
 * execution service is stateless apart from its own task table (contract §1).
 */
@Component
public class StartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final ApiExecutionService executionService;

    public StartupRecovery(ApiExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            executionService.recoverInterrupted();
        } catch (RuntimeException e) {
            log.warn("启动恢复未完成任务失败（忽略）: {}", e.getMessage());
        }
    }
}

package com.testhub.apiexec.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Startup recovery: RUNNING tasks from the previous process are marked FAILED
 * and every PENDING task is re-enqueued (idempotently) before workers start.
 * The execution service is stateless apart from its own task table and queue.
 */
@Component
@Order(0)
public class StartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final ApiExecutionService executionService;
    private final com.testhub.apiexec.scenario.ScenarioExecutionService scenarioService;

    public StartupRecovery(ApiExecutionService executionService,
                           com.testhub.apiexec.scenario.ScenarioExecutionService scenarioService) {
        this.executionService = executionService;
        this.scenarioService = scenarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            executionService.recoverInterrupted();
            scenarioService.recoverInterrupted();
        } catch (RuntimeException e) {
            log.warn("启动恢复未完成任务失败（忽略）: {}", e.getMessage());
        }
    }
}

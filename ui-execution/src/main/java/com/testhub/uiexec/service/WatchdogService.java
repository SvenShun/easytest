package com.testhub.uiexec.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Watchdog: task timeouts and agent heartbeat loss (docs/API-CONTRACT.md §6).
 * Disabled in tests via {@code testhub.uiexec.watchdog-enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "watchdog-enabled", havingValue = "true",
        matchIfMissing = true)
public class WatchdogService {

    private static final Logger log = LoggerFactory.getLogger(WatchdogService.class);

    private final TaskService taskService;

    public WatchdogService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelay = 10_000)
    public void tick() {
        try {
            taskService.checkTimeouts();
        } catch (Exception e) {
            log.warn("看门狗超时扫描失败: {}", e.getMessage(), e);
        }
        try {
            taskService.checkLostAgents();
        } catch (Exception e) {
            log.warn("看门狗心跳扫描失败: {}", e.getMessage(), e);
        }
    }
}

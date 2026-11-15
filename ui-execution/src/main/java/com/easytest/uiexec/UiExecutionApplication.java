package com.easytest.uiexec;

import com.easytest.uiexec.config.UiExecProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ui-execution (port 8081): orchestration for UI test tasks — executor routing,
 * per-device FIFO queues, state machine, traces, failure reports, skills pointer,
 * statistics and audit. Reuses the common JWT / service-token / agent-token
 * security layer from {@code easytest-common}.
 */
@SpringBootApplication(scanBasePackages = "com.easytest")
@EnableScheduling
@EnableConfigurationProperties(UiExecProperties.class)
public class UiExecutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(UiExecutionApplication.class, args);
    }
}

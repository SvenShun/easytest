package com.testhub.apiexec;

import com.testhub.apiexec.config.ApiExecProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * api-execution (port 8084): independently deployable HTTP API test executor.
 *
 * <p>The service accepts API test tasks, executes them on a bounded FIFO worker
 * pool with the JDK {@link java.net.http.HttpClient}, evaluates assertions and
 * reports terminal results back to testhub-server. It reuses the common
 * JWT / service-token security layer from {@code testhub-common}.</p>
 */
@SpringBootApplication(scanBasePackages = "com.testhub")
@EnableConfigurationProperties(ApiExecProperties.class)
public class ApiExecutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiExecutionApplication.class, args);
    }
}

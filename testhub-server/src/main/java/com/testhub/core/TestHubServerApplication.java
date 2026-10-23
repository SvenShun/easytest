package com.testhub.core;

import com.testhub.core.config.ArtifactProperties;
import com.testhub.core.config.CoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * TestHub core server: identity, requirements, client cases, script projects,
 * test plans, AI analysis and system settings.
 */
@SpringBootApplication(scanBasePackages = "com.testhub")
@EnableConfigurationProperties({CoreProperties.class, ArtifactProperties.class})
public class TestHubServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestHubServerApplication.class, args);
    }
}

package com.easytest.core;

import com.easytest.core.config.ArtifactProperties;
import com.easytest.core.config.CoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * EasyTest core server: identity, requirements, client cases, script projects,
 * test plans, AI analysis and system settings.
 */
@SpringBootApplication(scanBasePackages = "com.easytest")
@EnableScheduling
@EnableConfigurationProperties({CoreProperties.class, ArtifactProperties.class})
public class EasyTestServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyTestServerApplication.class, args);
    }
}

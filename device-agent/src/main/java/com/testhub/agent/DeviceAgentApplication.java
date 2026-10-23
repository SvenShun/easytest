package com.testhub.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.testhub.agent.config.AgentProperties;

/**
 * TestHub device-agent: device sub-server.
 *
 * <p>Runs on port 8090, no database. Exposes the dispatch endpoints consumed by
 * ui-execution and the local device driver endpoints consumed by ai-runtime.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class DeviceAgentApplication {

    public static void main(String[] args) {
        // PNG rendering must work on servers without a display.
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(DeviceAgentApplication.class, args);
    }
}

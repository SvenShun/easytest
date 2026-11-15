package com.easytest.iotlocal.service;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Properties;

@Configuration
public class MqttBrokerConfig {
    private static final Logger log = LoggerFactory.getLogger(MqttBrokerConfig.class);
    private Server server;

    @PostConstruct
    public void start() throws Exception {
        Properties props = new Properties();
        props.setProperty("host", "0.0.0.0");
        props.setProperty("port", "1883");
        props.setProperty("allow_anonymous", "true");
        props.setProperty("persistence_enabled", "false");
        server = new Server();
        server.startServer(new MemoryConfig(props));
        log.info("local MQTT broker started on 127.0.0.1:1883");
    }

    @PreDestroy
    public void stop() {
        if (server != null) server.stopServer();
    }
}

package com.easytest.iotlocal.service;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeviceMqttListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceMqttListener.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private MqttClient client;

    public DeviceMqttListener(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc; this.kafka = kafka;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() throws Exception {
        client = new MqttClient("tcp://127.0.0.1:1883", "iot-device-service-" + System.nanoTime());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        client.connect(options);
        client.subscribe("iot/device/+/command", (topic, message) -> {
            String[] parts = topic.split("/");
            String deviceId = parts.length > 2 ? parts[2] : "unknown";
            String payload = new String(message.getPayload());
            log.info("MQTT command device={} payload={}", deviceId, payload);
            jdbc.update("INSERT INTO device_command(device_id, payload) VALUES (?, ?)", deviceId, payload);
            jdbc.update("UPDATE device SET status='CONTROLLED' WHERE device_id = ?", deviceId);
            kafka.send("iot-device-events", deviceId,
                    "{\"event\":\"DEVICE_CONTROLLED\",\"deviceId\":\"" + deviceId + "\"}");
        });
        log.info("device service subscribed to MQTT command topic");
    }

    @jakarta.annotation.PreDestroy
    public void close() throws Exception {
        if (client != null && client.isConnected()) client.disconnect();
        if (client != null) client.close();
    }
}

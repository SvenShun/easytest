package com.easytest.iot.config;

import java.util.Map;

/** Environment-driven endpoints. Real deployments inject these via CI/agent env. */
public final class IotConfig {

    private IotConfig() {
    }

    public static String gateway() {
        return env("IOT_GATEWAY_URL", "http://127.0.0.1:8000");
    }

    public static String mysqlUrl() {
        return env("IOT_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/iot_home");
    }

    public static String mysqlUser() {
        return env("IOT_MYSQL_USER", "iot");
    }

    public static String mysqlPassword() {
        return env("IOT_MYSQL_PASSWORD", "iot");
    }

    public static String kafka() {
        return env("IOT_KAFKA_BOOTSTRAP", "127.0.0.1:9092");
    }

    public static String mqttHost() {
        return env("IOT_MQTT_HOST", "127.0.0.1");
    }

    public static int mqttPort() {
        return Integer.parseInt(env("IOT_MQTT_PORT", "1883"));
    }

    public static String redisHost() {
        return env("IOT_REDIS_HOST", "127.0.0.1");
    }

    public static int redisPort() {
        return Integer.parseInt(env("IOT_REDIS_PORT", "6379"));
    }

    public static Map<String, String> jsonHeaders() {
        return Map.of("Content-Type", "application/json");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}

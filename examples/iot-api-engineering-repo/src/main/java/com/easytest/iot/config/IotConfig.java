package com.easytest.iot.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Centralized IoT test environment: gateway, per-app MySQL, Kafka/MQTT/Redis. */
public final class IotConfig {

    private static final Map<String, Object> ROOT = load();
    private static final Map<String, Object> MYSQL = map(ROOT.get("mysql"));
    private static final Map<String, Object> KAFKA = map(ROOT.get("kafka"));
    private static final Map<String, Object> MQTT = map(ROOT.get("mqtt"));
    private static final Map<String, Object> REDIS = map(ROOT.get("redis"));

    private IotConfig() {
    }

    public static String gateway() {
        return env("IOT_GATEWAY_URL", string(ROOT.get("gateway"), "http://127.0.0.1:18080"));
    }

    public record DbConfig(String url, String username, String password) {
    }

    public static DbConfig mysql(String app) {
        String key = app == null || app.isBlank() ? "home" : app;
        Map<String, Object> db = map(MYSQL.get(key));
        return new DbConfig(
                env("IOT_MYSQL_" + key.toUpperCase() + "_URL", string(db.get("url"), "jdbc:mysql://127.0.0.1:3306/iot_" + key)),
                env("IOT_MYSQL_" + key.toUpperCase() + "_USER", string(db.get("user"), "iot")),
                env("IOT_MYSQL_" + key.toUpperCase() + "_PASSWORD", string(db.get("password"), "iot")));
    }

    public static String mysqlUrl() {
        return mysql("home").url();
    }

    public static String mysqlUser() {
        return mysql("home").username();
    }

    public static String mysqlPassword() {
        return mysql("home").password();
    }

    public static String kafka() {
        return env("IOT_KAFKA_BOOTSTRAP", string(KAFKA.get("bootstrap"), "127.0.0.1:9092"));
    }

    public static String topic(String name) {
        return string(map(KAFKA.get("topics")).get(name), "iot-device-events");
    }

    public static String mqttHost() {
        return env("IOT_MQTT_HOST", string(MQTT.get("host"), "127.0.0.1"));
    }

    public static int mqttPort() {
        return Integer.parseInt(env("IOT_MQTT_PORT", string(MQTT.get("port"), "1883")));
    }

    public static String mqttTopic(String name, String deviceId) {
        return string(map(MQTT.get("topics")).get(name), "iot/device/" + deviceId + "/" + name)
                .replace("{deviceId}", deviceId == null ? "" : deviceId);
    }

    public static String redisHost() {
        return env("IOT_REDIS_HOST", string(REDIS.get("host"), "127.0.0.1"));
    }

    public static int redisPort() {
        return Integer.parseInt(env("IOT_REDIS_PORT", string(REDIS.get("port"), "6379")));
    }

    public static Map<String, String> jsonHeaders() {
        return Map.of("Content-Type", "application/json");
    }

    private static Map<String, Object> load() {
        try (InputStream stream = IotConfig.class.getClassLoader().getResourceAsStream("iot-test.yaml")) {
            if (stream == null) return new LinkedHashMap<>();
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
            return map(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("加载 iot-test.yaml 失败: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> map(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private static String string(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}

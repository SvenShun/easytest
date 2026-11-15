package com.easytest.iot.template;

/** Shared request payload templates; enterprise-specific fields live here only. */
public final class RequestTemplates {

    private RequestTemplates() {
    }

    public static String login(String username, String password) {
        return "{\"username\":\"" + escape(username) + "\",\"password\":\"" + escape(password) + "\"}";
    }

    public static String bindDevice(String homeId, String deviceId) {
        return "{\"homeId\":\"" + escape(homeId) + "\",\"deviceId\":\"" + escape(deviceId) + "\"}";
    }

    public static String deviceOnline(String status) {
        return "{\"status\":\"" + escape(status) + "\"}";
    }

    public static String deviceCommand(String command) {
        return "{\"command\":\"" + escape(command) + "\"}";
    }

    public static String deviceEvent(String event, String deviceId) {
        return "{\"event\":\"" + escape(event) + "\",\"deviceId\":\"" + escape(deviceId) + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

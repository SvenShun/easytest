package com.testhub.apiexec.queue;

/** Normalizes the configurable Redis key prefix so keys compose predictably. */
final class RedisKeyPrefix {

    static final String DEFAULT = "testhub:apiexec:";

    private RedisKeyPrefix() {
    }

    static String normalize(String prefix) {
        String value = prefix == null || prefix.isBlank() ? DEFAULT : prefix.trim();
        return value.endsWith(":") ? value : value + ":";
    }
}

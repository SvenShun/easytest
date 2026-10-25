package com.testhub.apiexec.execution;

import java.util.List;
import java.util.Map;

/** Captured HTTP response used by the assertion engine and report builder. */
public record HttpExchangeResult(int status, Map<String, List<String>> headers, String body, long durationMs) {
}

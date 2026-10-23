package com.testhub.agent.dispatch;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.testhub.agent.http.HttpJsonClient;

/**
 * Posts task callbacks to ui-execution: step, batched logs and final result.
 * All calls carry {@code X-Agent-Token}; transient failures are logged and do
 * not abort the running task.
 */
@Component
public class CallbackClient {

    private static final Logger log = LoggerFactory.getLogger(CallbackClient.class);

    private final HttpJsonClient http;

    public CallbackClient(HttpJsonClient http) {
        this.http = http;
    }

    public boolean postLogs(String url, String token, List<Payloads.LogLine> lines) {
        if (isBlank(url) || lines == null || lines.isEmpty()) {
            return false;
        }
        return post(url, token, new Payloads.LogBatch(lines), Duration.ofSeconds(10), "logs");
    }

    public boolean postStep(String url, String token, Payloads.Step step) {
        if (isBlank(url)) {
            return false;
        }
        return post(url, token, step, Duration.ofSeconds(20), "step");
    }

    public boolean postResult(String url, String token, Payloads.TaskResult result) {
        if (isBlank(url)) {
            log.warn("resultUrl is empty; final status {} kept locally", result.status());
            return false;
        }
        return post(url, token, result, Duration.ofSeconds(30), "result");
    }

    private boolean post(String url, String token, Object body, Duration timeout, String kind) {
        try {
            HttpJsonClient.Response response = http.postJson(url, body,
                    Map.of("X-Agent-Token", token == null ? "" : token), timeout);
            if (response.ok()) {
                return true;
            }
            if (response.status() == 409) {
                log.info("ui-execution rejected duplicate {} callback (409, task already finished)", kind);
            } else {
                log.warn("{} callback failed: HTTP {} {}", kind, response.status(), response.body());
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("{} callback to {} failed: {}", kind, url, e.getMessage());
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.testhub.apiexec.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around the JDK 11 {@link HttpClient}. Requests are asynchronous
 * so a cancellation request can abort the in-flight exchange.
 */
@Component
public class HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CompletableFuture<HttpExchangeResult> executeAsync(HttpRequestSpec spec) {
        long startedAt = System.nanoTime();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(spec.url()))
                .timeout(Duration.ofSeconds(Math.max(1, spec.timeoutSec())));
        applyHeaders(builder, spec.headers());

        String method = spec.method() == null ? "GET" : spec.method().trim().toUpperCase();
        String body = spec.body();
        boolean sendBody = body != null && !"GET".equals(method) && !"HEAD".equals(method);
        if (sendBody) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> new HttpExchangeResult(
                        response.statusCode(),
                        response.headers().map(),
                        response.body(),
                        elapsedMillis(startedAt)));
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            try {
                builder.header(entry.getKey().trim(), entry.getValue());
            } catch (IllegalArgumentException e) {
                // Restricted (Host, Content-Length, ...) or malformed header: skip instead of failing the task.
                log.debug("忽略非法请求头 {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    public static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}

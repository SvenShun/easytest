package com.easytest.engineering;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reusable HTTP atom: gateway/client calls, JSON/text payloads and simple response parsing. */
public final class HttpAtom {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private HttpAtom() {
    }

    public record Response(int status, String body, Map<String, String> headers) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    public static Response request(String method, String url, String body, Map<String, String> headers,
                                   int timeoutMs) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(1, timeoutMs)))
                    .header("Accept", "application/json");
            if (headers != null) headers.forEach(builder::header);
            if (body == null) {
                builder.method(method == null ? "GET" : method.toUpperCase(), HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", headers != null && headers.containsKey("Content-Type")
                        ? headers.get("Content-Type") : "application/json");
                builder.method(method == null ? "POST" : method.toUpperCase(),
                        HttpRequest.BodyPublishers.ofString(body));
            }
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((key, values) -> {
                if (!values.isEmpty()) responseHeaders.put(key, values.get(0));
            });
            return new Response(response.statusCode(), response.body(), responseHeaders);
        } catch (Exception e) {
            throw new IllegalStateException("HTTP atom failed: " + e.getMessage(), e);
        }
    }

    public static Response get(String url, Map<String, String> headers, int timeoutMs) {
        return request("GET", url, null, headers, timeoutMs);
    }

    public static Response postJson(String url, String body, Map<String, String> headers, int timeoutMs) {
        return request("POST", url, body, headers, timeoutMs);
    }
}

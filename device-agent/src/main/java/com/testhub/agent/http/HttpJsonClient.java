package com.testhub.agent.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin JSON HTTP helper shared by registration, callbacks and the AI Runtime bridge.
 */
@Component
public class HttpJsonClient {

    private final HttpClient client;
    private final ObjectMapper mapper;

    public HttpJsonClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public Response postJson(String url, Object body, Map<String, String> headers, Duration timeout) {
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new HttpCallException("cannot serialize request body for " + url, e);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return send(builder.build());
    }

    public Response get(String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET();
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return send(builder.build());
    }

    /** Download binary content (script bundles, screenshots). */
    public byte[] getBytes(String url, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpCallException("GET " + url + " returned " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new HttpCallException("GET " + url + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpCallException("GET " + url + " interrupted", e);
        }
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new HttpCallException(request.method() + " " + request.uri() + " failed: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpCallException(request.method() + " " + request.uri() + " interrupted", e);
        }
    }

    public JsonNode readTree(String body) {
        try {
            return body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new HttpCallException("cannot parse JSON response: " + e.getOriginalMessage(), e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /** Response wrapper preserving the status code and raw body. */
    public record Response(int status, String body) {

        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** Runtime exception used when the remote endpoint cannot be reached. */
    public static class HttpCallException extends RuntimeException {

        public HttpCallException(String message) {
            super(message);
        }

        public HttpCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

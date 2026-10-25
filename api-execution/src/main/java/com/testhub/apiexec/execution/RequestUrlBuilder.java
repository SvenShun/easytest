package com.testhub.apiexec.execution;

import com.testhub.common.web.ApiException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Builds the absolute request URL from baseUrl + path + query parameters. */
public final class RequestUrlBuilder {

    private RequestUrlBuilder() {
    }

    public static String build(String baseUrl, String path, Map<String, String> query) {
        String rawPath = path == null ? "" : path.trim();
        String url;
        if (isAbsoluteHttpUrl(rawPath)) {
            url = rawPath;
        } else {
            String base = baseUrl == null ? "" : baseUrl.trim();
            if (base.isEmpty()) {
                throw ApiException.badRequest("baseUrl 不能为空");
            }
            if (!isAbsoluteHttpUrl(base)) {
                throw ApiException.badRequest("baseUrl 必须以 http:// 或 https:// 开头: " + base);
            }
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            if (!rawPath.isEmpty() && !rawPath.startsWith("/")) {
                rawPath = "/" + rawPath;
            }
            url = base + rawPath;
        }
        validate(url);
        String queryString = encodeQuery(query);
        if (queryString.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + queryString;
    }

    static String encodeQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static boolean isAbsoluteHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static void validate(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw ApiException.badRequest("非法的请求 URL: " + url);
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("非法的请求 URL: " + url);
        }
    }
}

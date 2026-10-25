package com.testhub.core.feature.apienv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.web.ApiException;
import com.testhub.core.feature.apienv.ApiEnvironmentDtos.AuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves environments into base URL + headers + variables, including auth
 * presets. LOGIN auth performs the login request once per plan trigger and
 * extracts a token using a JSON path such as {@code $.data.token}.
 */
@Service
public class ApiEnvironmentResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiEnvironmentResolver.class);
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper objectMapper;
    private final RestClient client;

    public ApiEnvironmentResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public ResolvedEnvironment resolve(ApiEnvironment environment) {
        if (environment == null || !environment.isEnabled()) {
            return new ResolvedEnvironment("", Map.of(), Map.of());
        }
        Map<String, String> variables = new LinkedHashMap<>(readMap(environment.getVariablesJson()));
        String baseUrl = apply(environment.getBaseUrl(), variables, Map.of());
        Map<String, String> headers = new LinkedHashMap<>();
        readMap(environment.getHeadersJson()).forEach((key, value) ->
                headers.put(apply(key, variables, Map.of()), apply(value, variables, Map.of())));
        AuthConfig auth = readAuth(environment.getAuthJson());
        switch (auth.type() == null ? "NONE" : auth.type().toUpperCase(Locale.ROOT)) {
            case "BEARER" -> {
                String token = apply(text(auth.token()), variables, Map.of());
                if (!token.isBlank()) {
                    headers.put("Authorization", "Bearer " + token);
                }
            }
            case "BASIC" -> {
                String username = apply(text(auth.username()), variables, Map.of());
                String password = apply(text(auth.password()), variables, Map.of());
                String encoded = Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
            case "API_KEY" -> {
                String name = apply(firstNonBlank(auth.headerName(), "X-API-Key"), variables, Map.of());
                String value = apply(firstNonBlank(auth.headerValue(), auth.token()), variables, Map.of());
                headers.put(name, value);
            }
            case "LOGIN" -> headers.putAll(login(environment, auth, baseUrl, headers, variables));
            default -> {
            }
        }
        return new ResolvedEnvironment(baseUrl, variables, headers);
    }

    public String apply(String text, ResolvedEnvironment environment, Map<String, Object> dataRow) {
        return apply(text, environment.variables(), dataRow == null ? Map.of() : dataRow);
    }

    public Map<String, String> applyMap(Map<String, String> source, ResolvedEnvironment environment,
                                        Map<String, Object> dataRow) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(
                apply(key, environment, dataRow), apply(value, environment, dataRow)));
        return result;
    }

    public List<Map<String, Object>> applyAssertions(List<Map<String, Object>> assertions,
                                                     ResolvedEnvironment environment,
                                                     Map<String, Object> dataRow) {
        if (assertions == null) {
            return List.of();
        }
        return assertions.stream().map(item -> {
            Map<String, Object> resolved = new LinkedHashMap<>();
            item.forEach((key, value) -> resolved.put(key, value instanceof String text
                    ? apply(text, environment, dataRow) : value));
            return resolved;
        }).toList();
    }

    private Map<String, String> login(ApiEnvironment environment, AuthConfig auth, String baseUrl,
                                      Map<String, String> baseHeaders, Map<String, String> variables) {
        String method = firstNonBlank(auth.loginMethod(), "POST").toUpperCase(Locale.ROOT);
        String path = apply(text(auth.loginPath()), variables, Map.of());
        String url = absolute(path) ? path : join(baseUrl, path);
        Map<String, String> headers = new LinkedHashMap<>(baseHeaders);
        if (auth.loginHeaders() != null) {
            auth.loginHeaders().forEach((key, value) ->
                    headers.put(apply(key, variables, Map.of()), apply(value, variables, Map.of())));
        }
        String body = auth.loginBody() == null ? null : apply(auth.loginBody(), variables, Map.of());
        if (body != null && !body.isBlank() && headers.keySet().stream()
                .noneMatch(key -> key.equalsIgnoreCase("Content-Type"))) {
            headers.put("Content-Type", "application/json");
        }
        try {
            String raw = client.method(HttpMethod.valueOf(method))
                    .uri(url)
                    .headers(h -> headers.forEach(h::set))
                    .body(body == null ? "" : body)
                    .retrieve()
                    .body(String.class);
            String token = readToken(raw, auth.tokenPath());
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("登录响应中未找到 tokenPath=" + auth.tokenPath());
            }
            String header = firstNonBlank(auth.tokenHeader(), "Authorization");
            String prefix = auth.tokenPrefix() == null ? "Bearer " : auth.tokenPrefix();
            String value = prefix.isEmpty() ? token : prefix + token;
            log.info("环境登录成功 env={} header={}", environment.getName(), header);
            return Map.of(header, value);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "ENV_AUTH_FAILED",
                    "环境鉴权失败 [" + environment.getName() + "]: " + e.getMessage());
        }
    }

    private String readToken(String raw, String tokenPath) {
        if (raw == null || raw.isBlank() || tokenPath == null || tokenPath.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            String path = tokenPath.trim();
            if (path.startsWith("$.")) {
                path = path.substring(2);
            } else if (path.startsWith("$")) {
                path = path.substring(1);
            }
            for (String segment : path.split("\\.")) {
                if (segment.isBlank()) {
                    continue;
                }
                node = node.path(segment);
            }
            return node.isMissingNode() || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            throw new IllegalStateException("登录响应不是合法 JSON: " + e.getMessage(), e);
        }
    }

    private String apply(String text, Map<String, String> variables, Map<String, Object> dataRow) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (int pass = 0; pass < 5; pass++) {
            Matcher matcher = VARIABLE.matcher(result);
            StringBuffer buffer = new StringBuffer();
            boolean found = false;
            while (matcher.find()) {
                found = true;
                String key = matcher.group(1).trim();
                String value = lookup(key, variables, dataRow);
                if (value == null) {
                    throw ApiException.badRequest("变量未定义: ${" + key + "}");
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(buffer);
            result = buffer.toString();
            if (!found) {
                break;
            }
        }
        return result;
    }

    private String lookup(String key, Map<String, String> variables, Map<String, Object> dataRow) {
        if (dataRow != null && dataRow.containsKey(key)) {
            Object value = dataRow.get(key);
            return value == null ? null : String.valueOf(value);
        }
        if (key.startsWith("data.") && dataRow != null) {
            Object value = dataRow.get(key.substring("data.".length()));
            return value == null ? null : String.valueOf(value);
        }
        return variables.get(key);
    }

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private AuthConfig readAuth(String json) {
        if (json == null || json.isBlank()) {
            return new AuthConfig("NONE", null, null, null, null, null, null,
                    null, null, Map.of(), null, null, null, null);
        }
        try {
            return objectMapper.readValue(json, AuthConfig.class);
        } catch (Exception e) {
            return new AuthConfig("NONE", null, null, null, null, null, null,
                    null, null, Map.of(), null, null, null, null);
        }
    }

    private static boolean absolute(String path) {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    private static String join(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    public record ResolvedEnvironment(String baseUrl, Map<String, String> variables,
                                      Map<String, String> headers) {
    }
}

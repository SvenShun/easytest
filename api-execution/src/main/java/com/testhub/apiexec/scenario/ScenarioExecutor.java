package com.testhub.apiexec.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.apiexec.assertion.ApiAssertion;
import com.testhub.apiexec.assertion.AssertionEvaluator;
import com.testhub.apiexec.assertion.AssertionResult;
import com.testhub.apiexec.execution.HttpExchangeResult;
import com.testhub.apiexec.execution.HttpExecutor;
import com.testhub.apiexec.execution.HttpRequestSpec;
import com.testhub.apiexec.execution.RequestUrlBuilder;
import com.testhub.common.web.ApiException;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic YAML scenario driver. Supports HTTP, SQL, Groovy, set, assert and wait steps,
 * variable interpolation between steps, JSON/SQL extraction and assertions.
 */
@Component
public class ScenarioExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScenarioExecutor.class);
    private static final Pattern TEMPLATE = Pattern.compile("\\$\\{([^}]+)}|\\{\\{\\s*([^}]+?)\\s*}}");
    private static final int MAX_BODY = 12_000;

    private final ScenarioYamlParser parser;
    private final HttpExecutor httpExecutor;
    private final AssertionEvaluator assertionEvaluator;
    private final ObjectMapper objectMapper;

    public ScenarioExecutor(ScenarioYamlParser parser, HttpExecutor httpExecutor,
                            AssertionEvaluator assertionEvaluator, ObjectMapper objectMapper) {
        this.parser = parser;
        this.httpExecutor = httpExecutor;
        this.assertionEvaluator = assertionEvaluator;
        this.objectMapper = objectMapper;
    }

    public ScenarioRunResult execute(String yaml, Map<String, Object> environment,
                                     Map<String, Object> requestVariables, int timeoutSec,
                                     BooleanSupplier cancelled) {
        long started = System.nanoTime();
        ScenarioDefinition definition = parser.parse(yaml);
        Map<String, Object> env = merge(definition.environment(), environment);
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> envVariables = map(env.get("variables"));
        variables.putAll(envVariables);
        variables.putAll(definition.variables());
        if (requestVariables != null) {
            variables.putAll(requestVariables);
        }
        variables.put("baseUrl", text(env.get("baseUrl")));
        variables.putIfAbsent("env", env);
        int totalTimeout = timeoutSec > 0 ? timeoutSec
                : definition.timeoutSec() == null ? 120 : Math.max(1, definition.timeoutSec());
        Instant deadline = Instant.now().plusSeconds(totalTimeout);
        boolean continueOnFailure = Boolean.TRUE.equals(definition.continueOnFailure());

        List<ScenarioStepResult> results = new ArrayList<>();
        int passed = 0;
        int seq = 1;
        Deque<Map<String, Object>> pending = new ArrayDeque<>(definition.steps());
        while (!pending.isEmpty()) {
            Map<String, Object> step = pending.removeFirst();
            if (controller(step)) {
                expandController(step, pending, variables, definition);
                continue;
            }
            if (cancelled != null && cancelled.getAsBoolean()) {
                return result("CANCELLED", "CANCELLED", "cancelled before step " + seq,
                        results, variables, started);
            }
            long remaining = Duration.between(Instant.now(), deadline).toMillis();
            if (remaining <= 0) {
                return result("TIMEOUT", "TIMEOUT", "scenario timed out after " + totalTimeout + "s",
                        results, variables, started);
            }
            String type = text(step.get("type"));
            String name = text(step.get("name"));
            if (name == null || name.isBlank()) {
                name = type + " #" + seq;
            }
            Map<String, Object> requestView = new LinkedHashMap<>();
            Map<String, Object> responseView = new LinkedHashMap<>();
            List<AssertionResult> assertions = new ArrayList<>();
            Map<String, Object> extracted = new LinkedHashMap<>();
            long stepStart = System.nanoTime();
            try {
                if (type == null || type.isBlank()) {
                    throw new StepFailure("STEP_INVALID", "step type is required");
                }
                switch (type.toLowerCase(Locale.ROOT)) {
                    case "http" -> executeHttp(step, variables, env, requestView, responseView, assertions,
                            extracted, remaining);
                    case "sql" -> executeSql(step, variables, env, requestView, responseView, assertions, extracted);
                    case "groovy" -> executeGroovy(step, variables, env, extracted);
                    case "set" -> executeSet(step, variables);
                    case "assert" -> executeAssert(step, variables, assertions);
                    case "wait" -> Thread.sleep(Math.max(0, Math.min(integer(step.get("ms"), 500), remaining)));
                    default -> throw new StepFailure("STEP_INVALID", "unsupported step type: " + type);
                }
                boolean failed = assertions.stream().anyMatch(item -> !item.passed());
                if (failed) {
                    throw new StepFailure("ASSERTION_FAILED", "assertion failed");
                }
                results.add(new ScenarioStepResult(seq, name, type, "OK",
                        elapsed(stepStart), requestView, responseView, assertions, extracted, null));
                passed++;
            } catch (StepFailure failure) {
                results.add(new ScenarioStepResult(seq, name, type, "FAIL",
                        elapsed(stepStart), requestView, responseView, assertions, extracted, failure.getMessage()));
                if (!continueOnFailure && !Boolean.TRUE.equals(step.get("continueOnFailure"))) {
                    return result(failure.reason.equals("TIMEOUT") ? "TIMEOUT" : "FAILED",
                            failure.reason, "step " + seq + " (" + name + ") failed: " + failure.getMessage(),
                            results, variables, started);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return result("CANCELLED", "CANCELLED", "interrupted at step " + seq, results, variables, started);
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                results.add(new ScenarioStepResult(seq, name, type, "FAIL",
                        elapsed(stepStart), requestView, responseView, assertions, extracted, message));
                if (!continueOnFailure && !Boolean.TRUE.equals(step.get("continueOnFailure"))) {
                    return result("FAILED", "STEP_FAILED", "step " + seq + " (" + name + ") failed: " + message,
                            results, variables, started);
                }
            }
            seq++;
        }
        return result("SUCCESS", null, passed + "/" + results.size() + " steps passed",
                results, variables, started);
    }

    private boolean controller(Map<String, Object> step) {
        String type = text(step.get("type"));
        return "if".equalsIgnoreCase(type) || "loop".equalsIgnoreCase(type);
    }

    private void expandController(Map<String, Object> step, Deque<Map<String, Object>> pending,
                                  Map<String, Object> variables, ScenarioDefinition definition) {
        String type = text(step.get("type"));
        if ("if".equalsIgnoreCase(type)) {
            boolean matches = condition(step, variables);
            Object key = matches ? step.get("steps") : step.get("elseSteps");
            pushFront(pending, maps(key));
            return;
        }
        List<Map<String, Object>> children = maps(step.get("steps"));
        if (children.isEmpty()) {
            return;
        }
        List<Map<String, Object>> iterations = new ArrayList<>();
        List<Map<String, Object>> dataset = datasetRows(step, definition);
        if (!dataset.isEmpty()) {
            iterations.addAll(dataset);
        } else {
            List<Object> items = array(step.get("items"));
            if (!items.isEmpty()) {
                for (Object item : items) {
                    iterations.add(mapOf("value", item));
                }
            } else {
                int times = Math.max(0, Math.min(integer(step.get("times"), 0), 100));
                for (int i = 0; i < times; i++) {
                    iterations.add(mapOf("value", i));
                }
            }
        }
        int max = Math.min(iterations.size(), 100);
        String indexVar = first(text(step.get("indexVar")), "index");
        String itemVar = first(text(step.get("itemVar")), "item");
        List<Map<String, Object>> expanded = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            Map<String, Object> row = iterations.get(i);
            Map<String, Object> setVariables = new LinkedHashMap<>();
            setVariables.put(indexVar, i);
            Object item = row.containsKey("value") ? row.get("value") : row;
            setVariables.put(itemVar, item);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!"value".equals(entry.getKey())) {
                    setVariables.put(entry.getKey(), entry.getValue());
                }
            }
            Map<String, Object> setStep = new LinkedHashMap<>();
            setStep.put("type", "set");
            setStep.put("name", "loop " + (i + 1));
            setStep.put("variables", setVariables);
            expanded.add(setStep);
            for (Map<String, Object> child : children) {
                expanded.add(new LinkedHashMap<>(child));
            }
        }
        pushFront(pending, expanded);
    }

    private List<Map<String, Object>> datasetRows(Map<String, Object> step, ScenarioDefinition definition) {
        String name = text(step.get("dataset"));
        if (name == null) {
            return List.of();
        }
        Object value = definition.datasets().get(name);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add(map(map));
                } else {
                    rows.add(mapOf("value", item));
                }
            }
        }
        return rows;
    }

    private void pushFront(Deque<Map<String, Object>> pending, List<Map<String, Object>> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            pending.addFirst(steps.get(i));
        }
    }

    private boolean condition(Map<String, Object> step, Map<String, Object> variables) {
        String left = interpolateText(text(step.get("left")), variables);
        String right = interpolateText(text(step.get("right")), variables);
        if (left != null) {
            return compare(left, right, first(text(step.get("operator")), "EQ"));
        }
        String expression = interpolateText(text(step.get("condition")), variables);
        if (expression == null || expression.isBlank()) {
            return false;
        }
        java.util.regex.Matcher matcher = Pattern.compile("(?i)\\s*(==|!=|>=|<=|>|<|contains)\\s*")
                .matcher(expression.trim());
        if (!matcher.find()) {
            return truthy(expression.trim());
        }
        String actual = expression.substring(0, matcher.start()).trim();
        String operator = matcher.group(1);
        String expected = expression.substring(matcher.end()).trim();
        return compare(unquote(actual), unquote(expected), operator);
    }

    private static boolean truthy(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return !normalized.isEmpty() && !"false".equals(normalized) && !"0".equals(normalized)
                && !"null".equals(normalized);
    }

    private static String unquote(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() >= 2 && ((text.startsWith("'") && text.endsWith("'"))
                || (text.startsWith("\"") && text.endsWith("\"")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private void executeHttp(Map<String, Object> step, Map<String, Object> variables,
                             Map<String, Object> env, Map<String, Object> requestView,
                             Map<String, Object> responseView, List<AssertionResult> assertions,
                             Map<String, Object> extracted, long remainingMs) throws Exception {
        String method = first(text(step.get("method")), "GET").toUpperCase(Locale.ROOT);
        String path = interpolateText(first(text(step.get("url")), text(step.get("path"))), variables);
        if (path == null) {
            throw new StepFailure("STEP_INVALID", "http step requires url or path");
        }
        Map<String, String> headers = new LinkedHashMap<>(stringMap(env.get("headers")));
        headers.putAll(stringMap(resolve(step.get("headers"), variables)));
        Map<String, String> query = stringMap(resolve(step.get("query"), variables));
        String baseUrl = text(variables.get("baseUrl"));
        String url = RequestUrlBuilder.build(baseUrl, path, query);
        String body = body(step.get("body"), variables);
        int timeout = Math.max(1, Math.min(integer(step.get("timeoutSec"), (int) (remainingMs / 1000)), 600));
        requestView.put("method", method);
        requestView.put("url", url);
        requestView.put("headers", headers);
        if (body != null) {
            requestView.put("body", truncate(body));
        }
        HttpRequestSpec spec = new HttpRequestSpec(method, url, headers, body, timeout);
        HttpExchangeResult response;
        try {
            response = httpExecutor.executeAsync(spec).get(timeout + 2L, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new StepFailure("TIMEOUT", "HTTP request timed out after " + timeout + "s");
        }
        responseView.put("status", response.status());
        responseView.put("headers", response.headers());
        responseView.put("body", truncate(response.body()));
        responseView.put("durationMs", response.durationMs());
        List<ApiAssertion> configured = assertions(step.get("assertions"), variables);
        assertions.addAll(assertionEvaluator.evaluate(configured, response));
        boolean hasStatus = configured.stream().anyMatch(a -> "STATUS_CODE".equalsIgnoreCase(a.type()));
        if (response.status() >= 400 && !hasStatus) {
            throw new StepFailure("HTTP_ERROR", "HTTP " + response.status() + " without STATUS_CODE assertion");
        }
        extractHttp(step.get("extract"), response, extracted, variables);
        if (assertions.stream().anyMatch(item -> !item.passed())) {
            throw new StepFailure("ASSERTION_FAILED", "HTTP assertions failed");
        }
    }

    private void executeSql(Map<String, Object> step, Map<String, Object> variables,
                            Map<String, Object> env, Map<String, Object> requestView,
                            Map<String, Object> responseView, List<AssertionResult> assertions,
                            Map<String, Object> extracted) {
        String query = interpolateText(text(step.get("query")), variables);
        if (query == null) {
            throw new StepFailure("STEP_INVALID", "sql step requires query");
        }
        Map<String, Object> config = datasource(step.get("datasource"), env);
        String url = text(config.get("url"));
        if (url == null) {
            throw new StepFailure("SQL_ERROR", "SQL datasource url is missing");
        }
        String driver = text(config.get("driverClass"));
        try {
            if (driver != null && !driver.isBlank()) {
                Class.forName(driver);
            }
            String username = text(config.get("username"));
            String password = text(config.get("password"));
            List<Object> params = array(resolve(step.get("params"), variables));
            requestView.put("url", url);
            requestView.put("query", query);
            requestView.put("params", params);
            try (Connection connection = username == null
                    ? DriverManager.getConnection(url)
                    : DriverManager.getConnection(url, username, password == null ? "" : password)) {
                boolean select = query.trim().toLowerCase(Locale.ROOT).matches("^(select|with|show|desc|explain)\\b.*");
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    for (int i = 0; i < params.size(); i++) {
                        statement.setObject(i + 1, params.get(i));
                    }
                    if (select) {
                        try (ResultSet resultSet = statement.executeQuery()) {
                            List<Map<String, Object>> rows = rows(resultSet);
                            responseView.put("rows", rows);
                            responseView.put("rowCount", rows.size());
                            extractSql(step.get("extract"), rows, 0, extracted, variables);
                            assertions.addAll(sqlAssertions(step.get("assertions"), rows, variables));
                        }
                    } else {
                        int changed = statement.executeUpdate();
                        responseView.put("updateCount", changed);
                        extractSql(step.get("extract"), List.of(), changed, extracted, variables);
                        assertions.addAll(sqlAssertions(step.get("assertions"), List.of(), variables));
                    }
                }
            }
        } catch (StepFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw new StepFailure("SQL_ERROR", "SQL execution failed: " + e.getMessage());
        }
        if (assertions.stream().anyMatch(item -> !item.passed())) {
            throw new StepFailure("ASSERTION_FAILED", "SQL assertions failed");
        }
    }

    private void executeGroovy(Map<String, Object> step, Map<String, Object> variables,
                               Map<String, Object> env, Map<String, Object> extracted) {
        String script = text(step.get("script"));
        if (script == null || script.isBlank()) {
            throw new StepFailure("STEP_INVALID", "groovy step requires script");
        }
        try {
            Binding binding = new Binding();
            binding.setVariable("vars", variables);
            binding.setVariable("env", env);
            binding.setVariable("log", log);
            binding.setVariable("json", objectMapper);
            Object result = new GroovyShell(getClass().getClassLoader(), binding).evaluate(script);
            if (result != null) {
                variables.put("_scriptResult", result);
            }
            Object extract = resolve(step.get("extract"), variables);
            if (extract instanceof Map<?, ?> map) {
                map.forEach((key, value) -> {
                    if (String.valueOf(value).equals("_scriptResult")) {
                        extracted.put(String.valueOf(key), result);
                        variables.put(String.valueOf(key), result);
                    }
                });
            }
        } catch (Exception e) {
            throw new StepFailure("SCRIPT_ERROR", "Groovy script failed: " + e.getMessage());
        }
    }

    private void executeSet(Map<String, Object> step, Map<String, Object> variables) {
        Map<String, Object> values = map(step.get("variables"));
        values.forEach((key, value) -> variables.put(key, resolve(value, variables)));
    }

    private void executeAssert(Map<String, Object> step, Map<String, Object> variables,
                               List<AssertionResult> assertions) {
        for (Map<String, Object> assertion : maps(step.get("assertions"))) {
            String type = first(text(assertion.get("type")), "VARIABLE").toUpperCase(Locale.ROOT);
            String path = text(assertion.get("path"));
            String operator = first(text(assertion.get("operator")), "EQ").toUpperCase(Locale.ROOT);
            String expected = interpolateText(text(assertion.get("expected")), variables);
            Object actualValue;
            if ("VARIABLE".equals(type)) {
                actualValue = lookup(variables, path);
            } else {
                actualValue = resolve(assertion.get("actual"), variables);
            }
            String actual = actualValue == null ? null : String.valueOf(actualValue);
            boolean passed = compare(actual, expected, operator);
            assertions.add(passed
                    ? AssertionResult.pass(type, path, operator, expected, actual)
                    : AssertionResult.fail(type, path, operator, expected, actual,
                    "expected " + operator + " " + expected + " but was " + actual));
        }
    }

    private void extractHttp(Object extractSpec, HttpExchangeResult response, Map<String, Object> extracted,
                             Map<String, Object> variables) {
        for (Map.Entry<String, Object> entry : map(extractSpec).entrySet()) {
            String expression = text(entry.getValue());
            Object value = null;
            if (expression != null) {
                if (expression.startsWith("$")) {
                    try {
                        JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
                        JsonNode node = AssertionEvaluator.JsonPathReader.read(root, expression);
                        value = node == null || node.isNull() ? null : node.isValueNode() ? node.asText() : node;
                    } catch (Exception e) {
                        throw new StepFailure("EXTRACT_FAILED", "JSON extract failed: " + e.getMessage());
                    }
                } else if (expression.toLowerCase(Locale.ROOT).startsWith("header:")) {
                    String name = expression.substring("header:".length());
                    response.headers().forEach((key, values) -> {
                        if (key.equalsIgnoreCase(name) && !values.isEmpty()) {
                            // cannot assign in lambda; handled below
                        }
                    });
                    for (Map.Entry<String, List<String>> header : response.headers().entrySet()) {
                        if (header.getKey().equalsIgnoreCase(name) && !header.getValue().isEmpty()) {
                            value = header.getValue().get(0);
                        }
                    }
                } else if ("status".equalsIgnoreCase(expression)) {
                    value = response.status();
                } else if ("body".equalsIgnoreCase(expression)) {
                    value = response.body();
                }
            }
            extracted.put(entry.getKey(), value);
            variables.put(entry.getKey(), value);
        }
    }

    private void extractSql(Object extractSpec, List<Map<String, Object>> rows, int updateCount,
                            Map<String, Object> extracted, Map<String, Object> variables) {
        for (Map.Entry<String, Object> entry : map(extractSpec).entrySet()) {
            String expression = text(entry.getValue());
            Object value = null;
            if ("rowCount".equalsIgnoreCase(expression)) {
                value = rows.size();
            } else if ("updateCount".equalsIgnoreCase(expression)) {
                value = updateCount;
            } else if (expression != null && expression.startsWith("rows")) {
                value = readPath(rows, expression);
            }
            extracted.put(entry.getKey(), value);
            variables.put(entry.getKey(), value);
        }
    }

    private List<AssertionResult> sqlAssertions(Object spec, List<Map<String, Object>> rows,
                                                Map<String, Object> variables) {
        List<AssertionResult> results = new ArrayList<>();
        for (Map<String, Object> item : maps(spec)) {
            String type = first(text(item.get("type")), "SQL_ROW_COUNT").toUpperCase(Locale.ROOT);
            String operator = first(text(item.get("operator")), "GT").toUpperCase(Locale.ROOT);
            String expected = interpolateText(text(item.get("expected")), variables);
            Object actual;
            String path = text(item.get("path"));
            if ("SQL_VALUE".equals(type)) {
                actual = path == null ? null : readPath(rows, path);
            } else {
                actual = rows.size();
            }
            String actualText = actual == null ? null : String.valueOf(actual);
            boolean passed = compare(actualText, expected, operator);
            results.add(passed
                    ? AssertionResult.pass(type, path, operator, expected, actualText)
                    : AssertionResult.fail(type, path, operator, expected, actualText,
                    "expected " + operator + " " + expected + " but was " + actualText));
        }
        return results;
    }

    private List<ApiAssertion> assertions(Object spec, Map<String, Object> variables) {
        List<ApiAssertion> result = new ArrayList<>();
        for (Map<String, Object> item : maps(spec)) {
            result.add(new ApiAssertion(text(item.get("type")), text(item.get("path")),
                    text(item.get("operator")), interpolateText(text(item.get("expected")), variables)));
        }
        return result;
    }

    private Map<String, Object> datasource(Object spec, Map<String, Object> env) {
        Map<String, Object> datasources = map(env.get("datasources"));
        if (spec instanceof Map<?, ?> inline) {
            return map(inline);
        }
        String name = text(spec);
        if (name == null) {
            name = "default";
        }
        Map<String, Object> config = map(datasources.get(name));
        if (config.isEmpty()) {
            config = map(env.get("datasource"));
        }
        return config;
    }

    private List<Map<String, Object>> rows(ResultSet resultSet) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = resultSet.getMetaData();
        int columns = meta.getColumnCount();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                String label = meta.getColumnLabel(i);
                Object cell = resultSet.getObject(i);
                row.put(label, cell);
                row.putIfAbsent(label.toLowerCase(Locale.ROOT), cell);
                row.putIfAbsent(label.toUpperCase(Locale.ROOT), cell);
            }
            rows.add(row);
            if (rows.size() >= 1000) {
                break;
            }
        }
        return rows;
    }

    private Object readPath(List<Map<String, Object>> rows, String expression) {
        if (expression == null || rows.isEmpty()) {
            return null;
        }
        String rest = expression.startsWith("rows") ? expression.substring(4) : expression;
        if (rest.startsWith("[")) {
            int end = rest.indexOf(']');
            if (end < 0) {
                return null;
            }
            int index;
            try {
                index = Integer.parseInt(rest.substring(1, end));
            } catch (NumberFormatException e) {
                return null;
            }
            if (index >= rows.size()) {
                return null;
            }
            rest = rest.substring(end + 1);
            if (rest.startsWith(".")) {
                rest = rest.substring(1);
            }
            return rest.isEmpty() ? rows.get(index) : cell(rows.get(index), rest);
        }
        if (rest.startsWith(".")) {
            rest = rest.substring(1);
        }
        return rest.isEmpty() ? rows : cell(rows.get(0), rest);
    }

    private Object cell(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String body(Object value, Map<String, Object> variables) {
        Object resolved = resolve(value, variables);
        if (resolved == null) {
            return null;
        }
        if (resolved instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(resolved);
        } catch (Exception e) {
            return String.valueOf(resolved);
        }
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        map(value).forEach((key, item) -> result.put(key, item == null ? null : String.valueOf(item)));
        return result;
    }

    private Object resolve(Object value, Map<String, Object> variables) {
        if (value instanceof String text) {
            Matcher matcher = TEMPLATE.matcher(text);
            if (matcher.matches()) {
                String key = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(2).trim();
                return lookup(variables, key);
            }
            StringBuilder builder = new StringBuilder();
            matcher.reset();
            while (matcher.find()) {
                String key = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(2).trim();
                Object resolved = lookup(variables, key);
                if (resolved == null) {
                    throw new StepFailure("VARIABLE_UNDEFINED", "变量未定义: " + key);
                }
                matcher.appendReplacement(builder, Matcher.quoteReplacement(String.valueOf(resolved)));
            }
            matcher.appendTail(builder);
            return builder.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), resolve(item, variables)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> resolve(item, variables)).toList();
        }
        return value;
    }

    private String interpolateText(String value, Map<String, Object> variables) {
        if (value == null) {
            return null;
        }
        Object resolved = resolve(value, variables);
        return resolved == null ? null : String.valueOf(resolved);
    }

    private Object lookup(Map<String, Object> variables, String key) {
        if (key == null) {
            return null;
        }
        if (variables.containsKey(key)) {
            return variables.get(key);
        }
        Object current = variables;
        for (String part : key.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private boolean compare(String actual, String expected, String operator) {
        if (actual == null || expected == null) {
            return actual == null && expected == null;
        }
        String op = operator == null ? "EQ" : operator.trim().toUpperCase(Locale.ROOT);
        op = switch (op) {
            case "==" -> "EQ";
            case "!=" -> "NE";
            case ">" -> "GT";
            case ">=" -> "GE";
            case "<" -> "LT";
            case "<=" -> "LE";
            default -> op;
        };
        if (op.equals("CONTAINS")) {
            return actual.contains(expected);
        }
        if (op.equals("NOT_CONTAINS")) {
            return !actual.contains(expected);
        }
        try {
            BigDecimal left = new BigDecimal(actual);
            BigDecimal right = new BigDecimal(expected);
            return switch (op) {
                case "GT" -> left.compareTo(right) > 0;
                case "GE" -> left.compareTo(right) >= 0;
                case "LT" -> left.compareTo(right) < 0;
                case "LE" -> left.compareTo(right) <= 0;
                case "NE" -> left.compareTo(right) != 0;
                default -> left.compareTo(right) == 0;
            };
        } catch (NumberFormatException e) {
            return switch (op) {
                case "NE" -> !actual.equals(expected);
                case "EQ" -> actual.equals(expected);
                default -> false;
            };
        }
    }

    private ScenarioRunResult result(String status, String reason, String summary,
                                     List<ScenarioStepResult> steps, Map<String, Object> variables,
                                     long started) {
        Map<String, Object> reportVariables = new LinkedHashMap<>(variables);
        reportVariables.remove("env");
        return new ScenarioRunResult(status, reason, summary, null == reason ? null : summary,
                steps, reportVariables, elapsed(started));
    }

    private static Map<String, Object> merge(Map<String, Object> first, Map<String, Object> second) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (first != null) {
            result.putAll(first);
        }
        if (second != null) {
            result.putAll(second);
        }
        return result;
    }

    private List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(map(map));
                }
            }
        }
        return result;
    }

    private List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private Map<String, Object> map(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String first(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static long elapsed(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_BODY ? value : value.substring(0, MAX_BODY) + "...";
    }

    private static final class StepFailure extends RuntimeException {
        private final String reason;

        private StepFailure(String reason, String message) {
            super(message);
            this.reason = reason;
        }
    }
}

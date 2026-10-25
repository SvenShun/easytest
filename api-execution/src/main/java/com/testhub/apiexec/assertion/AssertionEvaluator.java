package com.testhub.apiexec.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.apiexec.execution.HttpExchangeResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evaluates the five assertion types from docs/API-EXECUTION-CONTRACT.md §4.
 * Evaluation is total: a malformed assertion yields a failed result instead of
 * aborting the task's report.
 */
@Component
public class AssertionEvaluator {

    private static final String STATUS_CODE = "STATUS_CODE";
    private static final String JSON_PATH = "JSON_PATH";
    private static final String BODY_CONTAINS = "BODY_CONTAINS";
    private static final String RESPONSE_TIME_MS = "RESPONSE_TIME_MS";
    private static final String HEADER_CONTAINS = "HEADER_CONTAINS";

    private final ObjectMapper objectMapper;

    public AssertionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AssertionResult> evaluate(List<ApiAssertion> assertions, HttpExchangeResult response) {
        List<AssertionResult> results = new ArrayList<>();
        if (assertions == null) {
            return results;
        }
        for (ApiAssertion assertion : assertions) {
            results.add(evaluateOne(assertion, response));
        }
        return results;
    }

    private AssertionResult evaluateOne(ApiAssertion assertion, HttpExchangeResult response) {
        if (assertion == null) {
            return AssertionResult.fail("UNKNOWN", null, null, null, null, "assertion is null");
        }
        String type = normalize(assertion.type());
        String operator = normalize(assertion.operator());
        try {
            if (operator.isEmpty()) {
                return fail(assertion, type, null, "operator is required");
            }
            return switch (type) {
                case STATUS_CODE -> compareNumber(assertion, type, operator,
                        response == null ? null : String.valueOf(response.status()),
                        response == null ? null : (long) response.status());
                case JSON_PATH -> evalJsonPath(assertion, type, operator, response);
                case BODY_CONTAINS -> compareText(assertion, type, operator,
                        response == null || response.body() == null ? "" : response.body(), type);
                case RESPONSE_TIME_MS -> compareNumber(assertion, type, operator,
                        response == null ? null : String.valueOf(response.durationMs()),
                        response == null ? null : response.durationMs());
                case HEADER_CONTAINS -> evalHeader(assertion, type, operator, response);
                default -> fail(assertion, type, null, "unsupported assertion type: " + assertion.type());
            };
        } catch (RuntimeException e) {
            return fail(assertion, type, null, "assertion error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------- JSON path

    private AssertionResult evalJsonPath(ApiAssertion assertion, String type, String operator,
                                         HttpExchangeResult response) {
        String path = assertion.path();
        if (path == null || path.isBlank()) {
            return fail(assertion, type, null, "path is required for JSON_PATH");
        }
        if (response == null || response.body() == null || response.body().isBlank()) {
            return fail(assertion, type, null, "response body is empty");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception e) {
            return fail(assertion, type, null, "response body is not valid JSON");
        }
        JsonNode node = JsonPathReader.read(root, path);
        if (node == null || node.isMissingNode()) {
            return fail(assertion, type, null, "json path not found: " + path);
        }
        String actual = display(node);
        if (node.isNumber() && isNumericOperator(operator)) {
            return compareNumber(assertion, type, operator, actual, node.decimalValue());
        }
        if (node.isBoolean() && (operator.equals("EQ") || operator.equals("NE"))) {
            boolean expected = Boolean.parseBoolean(normalize(assertion.expected()));
            boolean passed = node.booleanValue() == expected;
            if (operator.equals("NE")) {
                passed = !passed;
            }
            return passed
                    ? AssertionResult.pass(type, path, operator, assertion.expected(), actual)
                    : AssertionResult.fail(type, path, operator, assertion.expected(), actual,
                    "expected " + (operator.equals("NE") ? "not " : "") + expected + " but was " + actual);
        }
        return compareText(assertion, type, operator, actual, path);
    }

    // ---------------------------------------------------------------- headers

    private AssertionResult evalHeader(ApiAssertion assertion, String type, String operator,
                                       HttpExchangeResult response) {
        String headerName = assertion.path();
        if (headerName == null || headerName.isBlank()) {
            return fail(assertion, type, null, "path (header name) is required for HEADER_CONTAINS");
        }
        String actual = null;
        if (response != null && response.headers() != null) {
            for (Map.Entry<String, List<String>> entry : response.headers().entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName.trim())) {
                    actual = entry.getValue() == null || entry.getValue().isEmpty()
                            ? "" : String.join(", ", entry.getValue());
                    break;
                }
            }
        }
        if (actual == null) {
            if ("NOT_CONTAINS".equals(operator) || "NE".equals(operator)) {
                return AssertionResult.pass(type, headerName, operator, assertion.expected(), null);
            }
            return AssertionResult.fail(type, headerName, operator, assertion.expected(), null,
                    "response header not found: " + headerName);
        }
        return compareText(assertion, type, operator, actual, headerName);
    }

    // -------------------------------------------------------------- comparison

    private AssertionResult compareNumber(ApiAssertion assertion, String type, String operator,
                                          String actualText, Number actual) {
        if (actual == null) {
            return fail(assertion, type, null, "actual value is missing");
        }
        String expectedText = assertion.expected();
        if (expectedText == null) {
            return fail(assertion, type, actualText, "expected value is missing");
        }
        String normalizedOperator = operator.toUpperCase(Locale.ROOT);
        if (normalizedOperator.equals("CONTAINS") || normalizedOperator.equals("NOT_CONTAINS")) {
            return compareText(assertion, type, normalizedOperator, actualText, type);
        }
        BigDecimal expected;
        try {
            expected = new BigDecimal(expectedText.trim());
        } catch (NumberFormatException e) {
            return fail(assertion, type, actualText, "expected is not a number: " + expectedText);
        }
        BigDecimal actualDecimal = actual instanceof BigDecimal decimal
                ? decimal : new BigDecimal(actual.toString());
        int comparison = actualDecimal.compareTo(expected);
        boolean passed = switch (normalizedOperator) {
            case "EQ" -> comparison == 0;
            case "NE" -> comparison != 0;
            case "LT" -> comparison < 0;
            case "GT" -> comparison > 0;
            default -> {
                yield false;
            }
        };
        if (!passed && !isNumericOperator(normalizedOperator)) {
            return fail(assertion, type, actualText, "unsupported operator: " + operator);
        }
        return passed
                ? AssertionResult.pass(type, assertion.path(), normalizedOperator, assertion.expected(), actualText)
                : AssertionResult.fail(type, assertion.path(), normalizedOperator, assertion.expected(), actualText,
                "expected " + normalizedOperator + " " + expectedText + " but was " + actualText);
    }

    private AssertionResult compareText(ApiAssertion assertion, String type, String operator,
                                        String actual, String path) {
        String normalizedOperator = operator.toUpperCase(Locale.ROOT);
        String expected = assertion.expected() == null ? "" : assertion.expected();
        String safeActual = actual == null ? "" : actual;
        boolean passed = switch (normalizedOperator) {
            case "EQ" -> safeActual.equals(expected);
            case "NE" -> !safeActual.equals(expected);
            case "CONTAINS" -> safeActual.contains(expected);
            case "NOT_CONTAINS" -> !safeActual.contains(expected);
            default -> false;
        };
        if (!isTextOperator(normalizedOperator)) {
            return fail(assertion, type, actual, "unsupported operator: " + operator);
        }
        return passed
                ? AssertionResult.pass(type, path, normalizedOperator, assertion.expected(), actual)
                : AssertionResult.fail(type, path, normalizedOperator, assertion.expected(), actual,
                "expected " + normalizedOperator + " '" + expected + "' but was '" + safeActual + "'");
    }

    private boolean isNumericOperator(String operator) {
        String normalized = normalize(operator);
        return normalized.equals("EQ") || normalized.equals("NE")
                || normalized.equals("LT") || normalized.equals("GT");
    }

    private boolean isTextOperator(String operator) {
        String normalized = normalize(operator);
        return normalized.equals("EQ") || normalized.equals("NE")
                || normalized.equals("CONTAINS") || normalized.equals("NOT_CONTAINS");
    }

    private AssertionResult fail(ApiAssertion assertion, String type, String actual, String message) {
        return AssertionResult.fail(type, assertion.path(), normalize(assertion.operator()),
                assertion.expected(), actual, message);
    }

    private String display(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isContainerNode()) {
            return node.toString();
        }
        return node.asText();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** Minimal JSON-path reader supporting {@code $.a.b[0]['c']}. */
    static final class JsonPathReader {

        private JsonPathReader() {
        }

        static JsonNode read(JsonNode root, String path) {
            if (root == null) {
                return null;
            }
            String expression = path == null ? "" : path.trim();
            if (expression.isEmpty() || "$".equals(expression)) {
                return root;
            }
            if (!expression.startsWith("$")) {
                return null;
            }
            JsonNode current = root;
            int index = 1;
            while (index < expression.length()) {
                char ch = expression.charAt(index);
                if (ch == '.') {
                    index++;
                    int start = index;
                    while (index < expression.length() && expression.charAt(index) != '.'
                            && expression.charAt(index) != '[') {
                        index++;
                    }
                    String field = expression.substring(start, index);
                    if (field.isEmpty() || !current.isObject()) {
                        return null;
                    }
                    current = current.get(field);
                } else if (ch == '[') {
                    int end = expression.indexOf(']', index);
                    if (end < 0) {
                        return null;
                    }
                    String token = expression.substring(index + 1, end).trim();
                    index = end + 1;
                    if (isQuoted(token)) {
                        String field = token.substring(1, token.length() - 1);
                        if (!current.isObject()) {
                            return null;
                        }
                        current = current.get(field);
                    } else {
                        int arrayIndex;
                        try {
                            arrayIndex = Integer.parseInt(token);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                        if (!current.isArray()) {
                            return null;
                        }
                        current = current.get(arrayIndex);
                    }
                } else {
                    return null;
                }
                if (current == null || current.isMissingNode()) {
                    return null;
                }
            }
            return current;
        }

        private static boolean isQuoted(String token) {
            return token.length() >= 2
                    && ((token.startsWith("'") && token.endsWith("'"))
                    || (token.startsWith("\"") && token.endsWith("\"")));
        }
    }
}

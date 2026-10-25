package com.testhub.apiexec.assertion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.apiexec.execution.HttpExchangeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for every assertion type and operator in contract §4. */
class AssertionEvaluatorTest {

    private static final String BODY = """
            {"code":0,"message":"success","ok":true,
             "data":{"total":2,"items":[{"name":"first"},{"name":"second"}]}}
            """;

    private final AssertionEvaluator evaluator = new AssertionEvaluator(new ObjectMapper());

    private final HttpExchangeResult response = new HttpExchangeResult(200,
            Map.of("Content-Type", List.of("application/json"), "X-Trace", List.of("trace-abc")),
            BODY, 123);

    private List<AssertionResult> evaluate(ApiAssertion... assertions) {
        return evaluator.evaluate(List.of(assertions), response);
    }

    private AssertionResult single(ApiAssertion assertion) {
        return evaluate(assertion).get(0);
    }

    @Test
    void allFiveTypesPassOnSuccessfulResponse() {
        List<AssertionResult> results = evaluate(
                ApiAssertion.statusCode("EQ", "200"),
                ApiAssertion.jsonPath("$.code", "EQ", "0"),
                ApiAssertion.bodyContains("CONTAINS", "success"),
                ApiAssertion.responseTimeMs("LT", "2000"),
                ApiAssertion.headerContains("X-Trace", "CONTAINS", "trace-abc"));

        assertThat(results).hasSize(5).allMatch(AssertionResult::passed);
        assertThat(results.get(1).actual()).isEqualTo("0");
        assertThat(results.get(4).actual()).isEqualTo("trace-abc");
    }

    @Test
    void statusCodeMismatchFailsWithActualValue() {
        AssertionResult result = single(ApiAssertion.statusCode("EQ", "500"));
        assertThat(result.passed()).isFalse();
        assertThat(result.actual()).isEqualTo("200");
        assertThat(result.expected()).isEqualTo("500");
    }

    @Test
    void statusCodeSupportsNeLtGt() {
        assertThat(single(ApiAssertion.statusCode("NE", "201")).passed()).isTrue();
        assertThat(single(ApiAssertion.statusCode("LT", "400")).passed()).isTrue();
        assertThat(single(ApiAssertion.statusCode("GT", "404")).passed()).isFalse();
    }

    @Test
    void jsonPathSupportsNestedObjectsArraysAndRoot() {
        assertThat(single(ApiAssertion.jsonPath("$.data.items[0].name", "EQ", "first")).passed()).isTrue();
        assertThat(single(ApiAssertion.jsonPath("$['data']['total']", "EQ", "2")).passed()).isTrue();
        assertThat(single(ApiAssertion.jsonPath("$.message", "CONTAINS", "ucce")).passed()).isTrue();
        assertThat(single(ApiAssertion.jsonPath("$", "CONTAINS", "\"code\":0")).passed()).isTrue();
    }

    @Test
    void jsonPathFailureAndMissingPathFail() {
        AssertionResult mismatch = single(ApiAssertion.jsonPath("$.code", "EQ", "9"));
        assertThat(mismatch.passed()).isFalse();
        assertThat(mismatch.actual()).isEqualTo("0");

        AssertionResult missing = single(ApiAssertion.jsonPath("$.nope", "EQ", "0"));
        assertThat(missing.passed()).isFalse();
        assertThat(missing.message()).contains("not found");
    }

    @Test
    void bodyContainsAndNotContains() {
        assertThat(single(ApiAssertion.bodyContains("CONTAINS", "success")).passed()).isTrue();
        assertThat(single(ApiAssertion.bodyContains("NOT_CONTAINS", "failure")).passed()).isTrue();
        assertThat(single(ApiAssertion.bodyContains("CONTAINS", "absent")).passed()).isFalse();
        assertThat(single(ApiAssertion.bodyContains("NOT_CONTAINS", "success")).passed()).isFalse();
    }

    @Test
    void responseTimeSupportsLtGtEqNe() {
        assertThat(single(ApiAssertion.responseTimeMs("LT", "1000")).passed()).isTrue();
        assertThat(single(ApiAssertion.responseTimeMs("GT", "1000")).passed()).isFalse();
        assertThat(single(ApiAssertion.responseTimeMs("EQ", "123")).passed()).isTrue();
        assertThat(single(ApiAssertion.responseTimeMs("NE", "123")).passed()).isFalse();
    }

    @Test
    void headerContainsIsCaseInsensitiveAndSupportsNe() {
        assertThat(single(ApiAssertion.headerContains("x-trace", "CONTAINS", "abc")).passed()).isTrue();
        assertThat(single(ApiAssertion.headerContains("X-Trace", "NOT_CONTAINS", "zzz")).passed()).isTrue();
        assertThat(single(ApiAssertion.headerContains("X-Trace", "NE", "other")).passed()).isTrue();
        assertThat(single(ApiAssertion.headerContains("X-Missing", "CONTAINS", "x")).passed()).isFalse();
    }

    @Test
    void malformedAssertionsFailWithoutThrowing() {
        assertThat(single(new ApiAssertion("NOPE", null, "EQ", "1")).passed()).isFalse();
        assertThat(single(new ApiAssertion("STATUS_CODE", null, null, "200")).passed()).isFalse();
        assertThat(single(new ApiAssertion("STATUS_CODE", null, "LT", "abc")).passed()).isFalse();
        assertThat(single(new ApiAssertion("BODY_CONTAINS", null, "LT", "1")).passed()).isFalse();

        HttpExchangeResult nonJson = new HttpExchangeResult(200, Map.of(), "not-json", 5);
        List<AssertionResult> results = evaluator.evaluate(
                List.of(ApiAssertion.jsonPath("$.code", "EQ", "0")), nonJson);
        assertThat(results.get(0).passed()).isFalse();
        assertThat(results.get(0).message()).contains("not valid JSON");
    }
}

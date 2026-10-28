package com.testhub.apiexec.assertion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.apiexec.execution.HttpExchangeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionEvaluatorXmlSchemaTest {

    private final AssertionEvaluator evaluator = new AssertionEvaluator(new ObjectMapper());

    @Test
    void evaluatesXmlXPath() {
        HttpExchangeResult response = new HttpExchangeResult(200, Map.of(), "<root><user id=\"7\">tester</user></root>", 5);
        List<AssertionResult> results = evaluator.evaluate(List.of(
                new ApiAssertion("XML_PATH", "/root/user", "EQ", "tester"),
                new ApiAssertion("XML_PATH", "/root/user/@id", "EQ", "7"),
                new ApiAssertion("XML_PATH", "/root/user", "CONTAINS", "tes")), response);
        assertThat(results).allMatch(AssertionResult::passed);
    }

    @Test
    void evaluatesJsonSchema() {
        String schema = "{\"type\":\"object\",\"required\":[\"id\",\"name\"],"
                + "\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"}}}";
        HttpExchangeResult valid = new HttpExchangeResult(200, Map.of(), "{\"id\":7,\"name\":\"tester\"}", 5);
        HttpExchangeResult invalid = new HttpExchangeResult(200, Map.of(), "{\"name\":\"tester\"}", 5);
        assertThat(evaluator.evaluate(List.of(new ApiAssertion("JSON_SCHEMA", null, "VALID", schema)), valid))
                .allMatch(AssertionResult::passed);
        assertThat(evaluator.evaluate(List.of(new ApiAssertion("JSON_SCHEMA", null, "VALID", schema)), invalid))
                .anyMatch(item -> !item.passed());
        assertThat(evaluator.evaluate(List.of(new ApiAssertion("JSON_SCHEMA", null, "INVALID", schema)), invalid))
                .allMatch(AssertionResult::passed);
    }

    @Test
    void rejectsMalformedXmlAndSchema() {
        HttpExchangeResult badXml = new HttpExchangeResult(200, Map.of(), "<root>", 1);
        assertThat(evaluator.evaluate(List.of(new ApiAssertion("XML_PATH", "/root", "EQ", "x")), badXml))
                .anyMatch(item -> !item.passed());
        HttpExchangeResult json = new HttpExchangeResult(200, Map.of(), "{}", 1);
        assertThat(evaluator.evaluate(List.of(new ApiAssertion("JSON_SCHEMA", null, "VALID", "{bad")), json))
                .anyMatch(item -> !item.passed());
    }
}

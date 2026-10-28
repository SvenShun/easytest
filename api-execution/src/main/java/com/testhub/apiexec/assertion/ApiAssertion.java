package com.testhub.apiexec.assertion;

/**
 * One assertion as sent by testhub-server (docs/API-EXECUTION-CONTRACT.md §4).
 *
 * @param type     STATUS_CODE | JSON_PATH | XML_PATH | JSON_SCHEMA | BODY_CONTAINS |
 *                 RESPONSE_TIME_MS | HEADER_CONTAINS
 * @param path     JSON path ({@code $.code}) or response header name, depending on type
 * @param operator EQ | NE | LT | GT | CONTAINS | NOT_CONTAINS
 * @param expected expected value rendered as text
 */
public record ApiAssertion(String type, String path, String operator, String expected) {

    public static ApiAssertion statusCode(String operator, String expected) {
        return new ApiAssertion("STATUS_CODE", null, operator, expected);
    }

    public static ApiAssertion jsonPath(String path, String operator, String expected) {
        return new ApiAssertion("JSON_PATH", path, operator, expected);
    }

    public static ApiAssertion bodyContains(String operator, String expected) {
        return new ApiAssertion("BODY_CONTAINS", null, operator, expected);
    }

    public static ApiAssertion responseTimeMs(String operator, String expected) {
        return new ApiAssertion("RESPONSE_TIME_MS", null, operator, expected);
    }

    public static ApiAssertion headerContains(String header, String operator, String expected) {
        return new ApiAssertion("HEADER_CONTAINS", header, operator, expected);
    }
}
